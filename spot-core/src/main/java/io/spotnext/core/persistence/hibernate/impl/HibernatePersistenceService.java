package io.spotnext.core.persistence.hibernate.impl;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.persistence.CacheRetrieveMode;
import javax.persistence.EntityGraph;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityNotFoundException;
import javax.persistence.PersistenceException;
import javax.persistence.PersistenceUnit;
import javax.persistence.Subgraph;
import javax.persistence.TransactionRequiredException;
import javax.persistence.Tuple;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Order;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.validation.ConstraintViolationException;
import javax.validation.ValidationException;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.hibernate.CacheMode;
import org.hibernate.HibernateException;
import org.hibernate.LockMode;
import org.hibernate.ScrollableResults;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.internal.FetchingScrollableResultsImpl;
import org.hibernate.internal.SessionImpl;
import org.hibernate.persister.entity.AbstractEntityPersister;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.query.Query;
import org.hibernate.stat.Statistics;
import org.hibernate.tool.hbm2ddl.SchemaExport;
import org.hibernate.tool.hbm2ddl.SchemaUpdate;
import org.hibernate.tool.schema.TargetType;
import org.hibernate.tool.schema.internal.ExceptionHandlerLoggedImpl;
import org.hibernate.tool.schema.spi.SchemaManagementException;
import org.hibernate.tool.schema.spi.SchemaManagementTool;
import org.hibernate.tool.schema.spi.SchemaManagementToolCoordinator;
import org.hibernate.tool.schema.spi.SchemaValidator;
import org.hibernate.transform.AliasToEntityMapResultTransformer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.DependsOn;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

//import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.spotnext.core.infrastructure.annotation.logging.Log;
import io.spotnext.core.infrastructure.exception.ModelNotFoundException;
import io.spotnext.core.infrastructure.exception.ModelSaveException;
import io.spotnext.core.infrastructure.exception.UnknownTypeException;
import io.spotnext.core.infrastructure.service.ConfigurationService;
import io.spotnext.core.infrastructure.service.ValidationService;
import io.spotnext.core.infrastructure.support.LogLevel;
import io.spotnext.core.infrastructure.support.Logger;
import io.spotnext.core.persistence.exception.ModelNotUniqueException;
import io.spotnext.core.persistence.exception.QueryException;
import io.spotnext.core.persistence.query.JpqlQuery;
import io.spotnext.core.persistence.query.ModelQuery;
import io.spotnext.core.persistence.query.QueryResult;
import io.spotnext.core.persistence.query.SortOrder;
import io.spotnext.core.persistence.query.SortOrder.OrderDirection;
import io.spotnext.core.persistence.service.TransactionService;
import io.spotnext.core.persistence.service.impl.AbstractPersistenceService;
import io.spotnext.infrastructure.annotation.Property;
import io.spotnext.infrastructure.type.Item;
import io.spotnext.infrastructure.type.ItemTypePropertyDefinition;
import io.spotnext.support.util.ClassUtil;
import io.spotnext.support.util.MiscUtil;

/**
 * <p>
 * HibernatePersistenceService class.
 * </p>
 *
 * @author mojo2012
 * @version 1.0
 * @since 1.0
 */
@DependsOn("typeService")
//@SuppressFBWarnings("BC_UNCONFIRMED_CAST_OF_RETURN_VALUE")
public class HibernatePersistenceService extends AbstractPersistenceService {

	@Value("${hibernate.jdbc.batch_size:}")
	private int jdbcBatchSize = 100;

	protected MetadataExtractorIntegrator metadataIntegrator = MetadataExtractorIntegrator.INSTANCE;

	@PersistenceUnit
	protected EntityManagerFactory entityManagerFactory;
	protected TransactionService transactionService;

	@Autowired
	protected ValidationService validationService;

	/**
	 * <p>
	 * Constructor for HibernatePersistenceService.
	 * </p>
	 *
	 * @param entityManagerFactory a {@link javax.persistence.EntityManagerFactory} object.
	 * @param transactionService   a {@link io.spotnext.core.persistence.service.TransactionService} object.
	 * @param configurationService a {@link io.spotnext.infrastructure.service.ConfigurationService} object.
	 * @param loggingService       a {@link io.spotnext.infrastructure.service.LoggingService} object.
	 */
	@Autowired
	public HibernatePersistenceService(EntityManagerFactory entityManagerFactory, TransactionService transactionService,
			ConfigurationService configurationService) {

		this.entityManagerFactory = entityManagerFactory;
		this.transactionService = transactionService;
		this.configurationService = configurationService;

		if (configurationService.getBoolean("core.setup.typesystem.initialize", false)) {
			initializeTypeSystem();
		}

		if (configurationService.getBoolean("core.setup.typesystem.update", false)) {
			updateTypeSystem();
		}

		validateTypeSystem();

		if (configurationService.getBoolean("cleantypesystem", false)) {
			Logger.info("Cleaning type system ... (not yet implemented)");
			clearTypeSystem();
		}

		Logger.info(String.format("Persistence service initialized"));
	}

	@Override
	public void initializeTypeSystem() {
		Logger.info("Initializing type system schema ...");

		final SchemaExport schemaExport = new SchemaExport();
		schemaExport.setHaltOnError(true);
		schemaExport.setFormat(true);
		schemaExport.setDelimiter(";");
		schemaExport.setOutputFile("db-schema.sql");

		try {
			// TODO will most likely fail, implement a pure JDBC "drop
			// database" approach?
			schemaExport.drop(EnumSet.of(TargetType.DATABASE), metadataIntegrator.getMetadata());
		} catch (final Exception e) {
			Logger.warn("Could not drop type system schema.");
		}

		schemaExport.createOnly(EnumSet.of(TargetType.DATABASE), metadataIntegrator.getMetadata());
	}

	@Override
	public void updateTypeSystem() {
		Logger.info("Updating type system schema ...");

		final SchemaUpdate schemaExport = new SchemaUpdate();
		schemaExport.setHaltOnError(true);
		schemaExport.setFormat(true);
		schemaExport.setDelimiter(";");
		schemaExport.setOutputFile("db-schema.sql");
		schemaExport.execute(EnumSet.of(TargetType.DATABASE), metadataIntegrator.getMetadata());
	}

	@Override
	public void validateTypeSystem() {
		final SchemaManagementTool tool = metadataIntegrator.getServiceRegistry()
				.getService(SchemaManagementTool.class);

		try {
			final SchemaValidator validator = tool.getSchemaValidator(entityManagerFactory.getProperties());
			validator.doValidation(metadataIntegrator.getMetadata(), SchemaManagementToolCoordinator
					.buildExecutionOptions(entityManagerFactory.getProperties(), ExceptionHandlerLoggedImpl.INSTANCE));

			Logger.debug("Type system schema seems to be OK");

		} catch (final SchemaManagementException e) {
			// currently hibernate throws a validation exception for float values that are being created as doubles ...
			// see https://hibernate.atlassian.net/browse/HHH-8690
			// so we hide that message in case we just did an initialization, otherwise it would look confusing in the logs
			if (!configurationService.getBoolean("core.setup.typesystem.initialize", false)) {
				Logger.warn("Type system schema needs to be initialized/updated");
			}
		}
	}

	protected void clearTypeSystem() {

	}

	private QueryResult executeQuery(JpqlQuery sourceQuery, Query query) {
		List values = new ArrayList<>();
		Integer totalCount = null;

		if (sourceQuery.getPageSize() > 0) {
			int start = (sourceQuery.getPage() > 0 ? sourceQuery.getPage() - 1 : 0) * sourceQuery.getPageSize();

			ScrollableResults scrollResult = null;

			try {
				scrollResult = query.scroll();

				if (start > 0) {
					scrollResult.scroll(start);
				}

				do {
					Object value = scrollResult.get();

					// this should actually not happen, but it does ...
					// TODO: check and fix null result objects
					if (value != null) {
						if (value.getClass().isArray()) {
							Object[] valueArray = (Object[]) value;
							if (valueArray.length > 0) {
								values.add(valueArray[0]);
							}
						} else {
							values.add(value);
						}
					}
				} while (values.size() < sourceQuery.getPageSize() && scrollResult.next());

				// go to last row to get max rows
				scrollResult.last();
				totalCount = scrollResult.getRowNumber();

				// different implementations handle this either with a start index of 0 or 1 ...
				if (!(scrollResult instanceof FetchingScrollableResultsImpl)) {
					totalCount += 1;
				}
			} finally {
				MiscUtil.closeQuietly(scrollResult);
			}
		} else {
			values = query.list();
			totalCount = values.size();
		}

		QueryResult result = new QueryResult(values, sourceQuery.getPage(), sourceQuery.getPageSize(), totalCount != null ? Long.valueOf(totalCount) : null);
		return result;
	}

	/** {@inheritDoc} */
	// @SuppressFBWarnings("REC_CATCH_EXCEPTION")
	@Override
	public <T> QueryResult<T> query(final io.spotnext.core.persistence.query.JpqlQuery<T> sourceQuery) throws QueryException {
		bindSession();

		try {
			return transactionService.execute(() -> {
				QueryResult<T> results = null;

				final Session session = getSession();
				session.setDefaultReadOnly(sourceQuery.isReadOnly());

				// if this is an item type, we just load the entities
				// if it is a "primitive" natively supported type we can also
				// just let hibernate do the work
				if (Item.class.isAssignableFrom(sourceQuery.getResultClass())
						|| NATIVE_DATATYPES.contains(sourceQuery.getResultClass())) {

					Query<T> query = null;

					try {
						query = session.createQuery(sourceQuery.getQuery(), sourceQuery.getResultClass());
					} catch (final Exception e) {
						throw new QueryException("Could not parse query", e);
					}

					setAccessLevel(sourceQuery, query);
					setCacheSettings(session, sourceQuery, query);
					setFetchSubGraphsHint(session, sourceQuery, query);
					setParameters(sourceQuery.getParams(), query);
//					setPagination(query, sourceQuery.getPage(), sourceQuery.getPageSize());

					results = executeQuery(sourceQuery, query);
				} else {
					// otherwise we load each value into a list of tuples
					// in that case the selected columns need to be aliased in
					// case the given result type has no constructor that exactly matches the returned
					// columns' types, as otherwise we cannot map the row values to properties.

					// only try to load results if the result type is not Void
					if (sourceQuery.isExecuteUpdate()) {
						final Query<Integer> query = session.createQuery(sourceQuery.getQuery());

						setAccessLevel(sourceQuery, (Query<T>) query);
						setParameters(sourceQuery.getParams(), query);

						int resultCode = query.executeUpdate();
						session.flush();
						if (sourceQuery.isClearCaches()) {
							session.clear();
						}

						boolean returnTypeSpecified = !Void.class.isAssignableFrom(sourceQuery.getResultClass());

						results = (QueryResult<T>) new QueryResult<Integer>(returnTypeSpecified ? Arrays.asList(resultCode) : null, 0, 0, null);
					} else {
						// fetch the temporary Tuple (!) result and convert it into the target type manually
						final Query<Tuple> query = session.createQuery(sourceQuery.getQuery(), Tuple.class);

						setAccessLevel(sourceQuery, (Query<T>) query);
						setParameters(sourceQuery.getParams(), query);

						if (Map.class.isAssignableFrom(sourceQuery.getResultClass())) {
							// all selected columns must specify an alias, otherwise the column value would not appear in the map!
							query.setResultTransformer(AliasToEntityMapResultTransformer.INSTANCE);
							results = executeQuery(sourceQuery, query);
						} else {
							final QueryResult<Tuple> tempResults = (QueryResult<Tuple>) executeQuery(sourceQuery, query);
							List<T> finalResults = new ArrayList<>();

							// if the return type is Tuple, a tuple array is returned
							// therefore we have to extract the first tuple first and use that as a base object
							// if no return type is set, it is only a Tuple
							for (final Object entry : tempResults.getResults()) {
								// first try to create the pojo using a constructor
								// that matches the result's column types

								Tuple t = null;

								if (entry != null && entry.getClass().isArray()) {
									Object[] entryArray = ((Object[]) entry);

									if (entryArray.length > 0) {
										t = (Tuple) entryArray[0];
									}
								} else if (entry instanceof Tuple) {
									t = (Tuple) entry;
								}

								if (t == null) {
									continue;
								}

								final Tuple tupleEntry = t;

								final List<Object> values = t.getElements().stream().map(e -> tupleEntry.get(e))
										.collect(Collectors.toList());

								if (Tuple.class.isAssignableFrom(sourceQuery.getResultClass())) {
									// if the only object in the tuple is an item, we can directly return it, otherwise we just return a list of values
									if (values != null && values.size() == 1 && values.get(0) instanceof Item) {
										finalResults.add((T) values.get(0));
									} else {
										finalResults.add((T) values);
									}
								} else {
									Optional<T> pojo = ClassUtil.instantiate(sourceQuery.getResultClass(), values.toArray());

									// if the POJO can't be instantiated, we try to
									// create it manually and inject the data using
									// reflection for this to work, each selected column
									// has to have the same alias as the pojo's
									// property!
									if (!pojo.isPresent()) {
										final Optional<T> obj = ClassUtil.instantiate(sourceQuery.getResultClass());

										if (obj.isPresent()) {
											final Object o = obj.get();
											t.getElements().stream()
													.forEach(el -> ClassUtil.setField(o, el.getAlias(), tupleEntry.get(el.getAlias())));
										}

										pojo = obj;
									}

									if (pojo.isPresent()) {
										finalResults.add(pojo.get());
									} else {
										throw new InstantiationException(String.format("Could not instantiate result type '%s'",
												sourceQuery.getResultClass()));
									}
								}
							}

							results = new QueryResult<>(finalResults, sourceQuery.getPage(), sourceQuery.getPageSize(), tempResults.getTotalCount());
						}
					}
				}
				return results;
			});
		} catch (final QueryException e) {
			throw e;
		} catch (final Exception e) {
			throw new QueryException(String.format("Could not execute query '%s'", sourceQuery.getQuery()), e);
		}
	}

	private <T, Q extends io.spotnext.core.persistence.query.Query<T>> void setAccessLevel(Q sourceQuery, Query<T> query) {
		query.setReadOnly(sourceQuery.isReadOnly());
	}

	protected <T, Q extends io.spotnext.core.persistence.query.Query<T>> void setCacheSettings(final Session session,
			final Q sourceQuery, final TypedQuery<T> query) {

		CacheMode cacheMode = CacheMode.NORMAL;

		if (!sourceQuery.isCachable() && !sourceQuery.isIgnoreCache()) {
			cacheMode = CacheMode.GET;
		} else if (!sourceQuery.isCachable() && sourceQuery.isIgnoreCache()) {
			cacheMode = CacheMode.IGNORE;
		} else if (sourceQuery.isCachable() && sourceQuery.isIgnoreCache()) {
			cacheMode = CacheMode.PUT;
		}

		session.setCacheMode(cacheMode);
//		query.setHint("org.hibernate.cacheable", sourceQuery.isCachable());
		query.setHint("javax.persistence.cache.retrieveMode", sourceQuery.isIgnoreCache() ? CacheRetrieveMode.BYPASS : CacheRetrieveMode.USE);
	}

	protected <T, Q extends io.spotnext.core.persistence.query.Query<T>> void setFetchSubGraphsHint(
			final Session session, final Q sourceQuery, final TypedQuery<T> query) throws UnknownTypeException {

		// TODO what about fetchgraph?

		final List<String> fetchSubGraphs = new ArrayList<>();

		if (sourceQuery.isEagerFetchRelations()) {
			final Map<String, ItemTypePropertyDefinition> props = typeService
					.getItemTypeProperties(typeService.getTypeCodeForClass((Class<Item>) sourceQuery.getResultClass()));

			// add all properties
			final List<String> validProperties = props.values().stream() //
					.filter(p -> Item.class.isAssignableFrom(p.getReturnType()) || p.getRelationDefinition() != null) //
					.map(p -> p.getName()) //
					.collect(Collectors.toList());
			fetchSubGraphs.addAll(validProperties);
		} else if (sourceQuery.getEagerFetchRelationProperties().size() > 0) {
			fetchSubGraphs.addAll(sourceQuery.getEagerFetchRelationProperties());
		}

		if (fetchSubGraphs.size() > 0) {
			if (!Item.class.isAssignableFrom(sourceQuery.getResultClass())) {
				Logger.debug("Fetch sub graphs can only be used for item queries - ignoring");
				return;
			}

			final EntityGraph<T> graph = session.createEntityGraph(sourceQuery.getResultClass());

			for (final String subgraph : fetchSubGraphs) {
				final Subgraph<?> itemGraph = graph.addSubgraph(subgraph);
			}

			query.setHint("javax.persistence.loadgraph", graph);
		}
	}

	protected <T> void setParameters(final Map<String, Object> params, final Query<T> query) {
		for (final Map.Entry<String, Object> entry : params.entrySet()) {
			if (NumberUtils.isCreatable(entry.getKey())) {
				query.setParameter(Integer.parseInt(entry.getKey()), entry.getValue());
			} else {
				query.setParameter(entry.getKey(), entry.getValue());
			}
		}
	}

	protected void setPagination(final javax.persistence.Query query, final int page, final int pageSize) {
		if (pageSize > 0) {
			query.setFirstResult((page > 0 ? page - 1 : 0) * pageSize);
			query.setMaxResults(pageSize);
		}
	}

	/** {@inheritDoc} */
	@Log(logLevel = LogLevel.DEBUG, measureExecutionTime = true, executionTimeThreshold = 100)
	@Override
	public <T extends Item> void save(final List<T> items) throws ModelSaveException, ModelNotUniqueException {
		bindSession();

		try {
			transactionService.execute(() -> {
				final Session session = getSession();
				int i = 0;

				try {
					for (final T item : items) {
						if (item.getVersion() == -1) {
							session.save(item);
						} else {
							session.saveOrUpdate(item);
						}

						// use same as the JDBC batch size
						if (i >= jdbcBatchSize && i % jdbcBatchSize == 0) {
							// flush a batch of inserts and release memory:
							session.flush();
						}
						i++;
					}

					// this is needed, otherwise saved entities are not
					session.flush();
					items.stream().forEach(o -> session.evict(o));
				} catch (final ValidationException e) {
					final String message;
					if (e instanceof ConstraintViolationException) {
						message = validationService
								.convertToReadableMessage(((ConstraintViolationException) e).getConstraintViolations());
					} else {
						message = e.getMessage();
					}

					throw new ModelSaveException(message, e);
				} catch (final DataIntegrityViolationException | TransactionRequiredException
						| IllegalArgumentException e) {

					throw new ModelSaveException("Could not save given items: " + e.getMessage(), e);

				} catch (final Exception e) {
					final Throwable rootCause = ExceptionUtils.getRootCause(e);
					final String rootCauseMessage = rootCause != null ? rootCause.getMessage() : e.getMessage();

					throw new ModelSaveException(rootCauseMessage, e);
				}

				return null;
			});
		} catch (final TransactionException e) {
			if (e.getCause() instanceof ModelSaveException) {
				throw (ModelSaveException) e.getCause();
			} else if (e.getCause() instanceof ModelNotUniqueException) {
				throw (ModelNotUniqueException) e.getCause();
			} else {
				throw e;
			}
		}
	}

	/** {@inheritDoc} */
	@Override
	public <T extends Item> T load(final Class<T> type, final long id, boolean returnProxy) throws ModelNotFoundException {
		bindSession();

		try {
			return transactionService.execute(() -> {
				T item = returnProxy ? getSession().load(type, id) : getSession().get(type, id);
				return item;
			});
		} catch (final TransactionException e) {
			if (e.getCause() instanceof ModelNotFoundException) {
				throw (ModelNotFoundException) e.getCause();
			} else {
				throw e;
			}
		}
	}

	/** {@inheritDoc} */
	@Override
	public <T extends Item> void refresh(final List<T> items) throws ModelNotFoundException {
		bindSession();

		try {
			transactionService.execute(() -> {
				for (final T item : items) {
					try {
						if (attach(item)) {
							getSession().refresh(item, LockMode.NONE);
						}
					} catch (DataIntegrityViolationException | HibernateException | TransactionRequiredException | IllegalArgumentException
							| EntityNotFoundException e) {
						throw new ModelNotFoundException(
								String.format("Could not refresh item with id=%s.", item.getId()), e);
					}
				}

				return null;
			});
		} catch (final TransactionException e) {
			if (e.getCause() instanceof ModelNotFoundException) {
				throw (ModelNotFoundException) e.getCause();
			} else {
				throw e;
			}
		}
	}

	/** {@inheritDoc} */
	@Override
	public <T extends Item> boolean attach(final T item) throws ModelNotFoundException {
		bindSession();

		try {
			// ignore unpersisted or already attached items
			if (isAttached(item)) {
				return true;
			}

			getSession().load(item, item.getId());
		} catch (HibernateException | TransactionRequiredException | IllegalArgumentException
				| EntityNotFoundException e) {
			throw new ModelNotFoundException(
					String.format("Could not attach item with id=%s to the current session.", item.getId()), e);
		}

		return false;
	}

	/** {@inheritDoc} */
	@Override
	public <T extends Item> List<T> load(final ModelQuery<T> sourceQuery) {

		bindSession();

		return transactionService.execute(() -> {

			final Session session = getSession();
			final CriteriaBuilder builder = session.getCriteriaBuilder();

			final CriteriaQuery<T> cq = builder.createQuery(sourceQuery.getResultClass());
			final Root<T> queryResultType = cq.from(sourceQuery.getResultClass());
			CriteriaQuery<T> itemSelect = cq.select(queryResultType);

			// check if we have to perform a separate query for pagination
			// hibernate can't handle pagination together with FETCH JOINs!
			boolean isIdQueryForPaginationNeeded = sourceQuery.getPageSize() > 0
					&& (sourceQuery.getEagerFetchRelationProperties().size() > 0 || sourceQuery.isEagerFetchRelations());
			boolean isSearchParametersDefined = MapUtils.isNotEmpty(sourceQuery.getSearchParameters());

			Predicate whereClause = null;

			if (isSearchParametersDefined) {
				whereClause = builder.conjunction();

				for (final Map.Entry<String, Object> entry : sourceQuery.getSearchParameters().entrySet()) {
					if (entry.getValue() instanceof Item && !((Item) entry.getValue()).isPersisted()) {
						throw new PersistenceException(String.format(
								"Passing non-persisted item as search param '%s' is not supported.", entry.getKey()));
					}

					whereClause = builder.and(whereClause, builder.equal(queryResultType.get(entry.getKey()), entry.getValue()));
				}
			}

			// always order by last created date and THEN ID, so we have a consistent ordering, even if new items are created
			// IDs are random, so they don't increment!
			boolean orderByNeeded = false;

			// make additional query to fetch the ids, applied the "maxResults" correctly
			if (isIdQueryForPaginationNeeded) {
				// we always have to order in case of a ID subquery for both queries!
				orderByNeeded = true;

				CriteriaQuery<Long> idCriteriaQuery = builder.createQuery(Long.class);
				final Root<T> idRoot = idCriteriaQuery.from(sourceQuery.getResultClass());
				idCriteriaQuery = idCriteriaQuery.select(idRoot.get(Item.PROPERTY_ID));

				// apply original where clause here, it will be indirectly applied to the original query using the fetched IDs
				if (whereClause != null) {
					idCriteriaQuery = idCriteriaQuery.where(whereClause);
				}

				// always apply the same order for all queries
				final TypedQuery<Long> idQuery = session.createQuery(idCriteriaQuery.orderBy(applyOrderBy(sourceQuery, builder, idRoot)));
				setPagination(idQuery, sourceQuery.getPage(), sourceQuery.getPageSize());

				final List<Long> idsToSelect = idQuery.getResultList();

				// only add where clause when there are actual IDs to select
				if (idsToSelect.size() > 0) {
					itemSelect = itemSelect.where(queryResultType.get(Item.PROPERTY_ID).in(idsToSelect));
				}
			} else {
				if (whereClause != null) {
					itemSelect = itemSelect.where(whereClause);
				}

				// if we have a single query, we only need to order if pagination is used
				if (sourceQuery.getOrderBy().size() > 0) {
					orderByNeeded = true;
				}
			}

			if (orderByNeeded) {
				// always apply the order here again, even if using id sub-query!
				itemSelect = itemSelect.orderBy(applyOrderBy(sourceQuery, builder, queryResultType));
			}

			final TypedQuery<T> query = session.createQuery(itemSelect);

			// only set these values if no fetch joins are used!
			// if we have fetch joins we just select by the ids that are fetched before using firstResult and maxResults
			if (!isIdQueryForPaginationNeeded) {
				setPagination(query, sourceQuery.getPage(), sourceQuery.getPageSize());
			}

			setFetchSubGraphsHint(session, sourceQuery, query);
			setCacheSettings(session, sourceQuery, query);

			final Query<T> queryObj = ((Query<T>) query);

			// set proper access level
			setAccessLevel(sourceQuery, queryObj);

			final List<T> results = queryObj.getResultList();

			return results;
		});
	}

	/**
	 * Generates the ORDER BY clause either for the {@link ModelQuery#getOrderBy()} or if empty for the default properties ({@link Item#PROPERTY_CREATED_AT} and
	 * {@link Item#PROPERTY_ID}).
	 * 
	 * @param sourceQuery
	 * @param builder
	 * @param root
	 * @return the generated order by clause
	 */
	protected Order[] applyOrderBy(final ModelQuery<?> sourceQuery, CriteriaBuilder builder, Root<?> root) {
		final List<Order> orderBys = new ArrayList<>();

		if (sourceQuery.getOrderBy().size() > 0) {
			for (SortOrder order : sourceQuery.getOrderBy()) {
				if (OrderDirection.ASC.equals(order.getDirection())) {
					orderBys.add(builder.asc(root.get(order.getColumnName())));
				} else {
					orderBys.add(builder.desc(root.get(order.getColumnName())));
				}
			}
		} else {
			orderBys.add(builder.asc(root.get(Item.PROPERTY_CREATED_AT)));
			orderBys.add(builder.asc(root.get(Item.PROPERTY_ID)));
		}

		return orderBys.toArray(new Order[orderBys.size()]);
	}

	/** {@inheritDoc} */
	@Override
	public <T extends Item> void remove(final List<T> items) {
		bindSession();

		transactionService.execute(() -> {
			for (final T item : items) {
				if (isAttached(item)) {
					getSession().remove(item);
				} else {
					remove(item.getClass(), item.getId());
				}
			}
			return null;
		});
	}

	/** {@inheritDoc} */
	@Override
	public <T extends Item> void remove(final Class<T> type, final long id) {
		bindSession();

		transactionService.execute(() -> {
			// TODO: improve
			// final String query = String.format("DELETE FROM %s WHERE id IN
			// (?id)", type.getSimpleName());

			// em.createQuery(query, type).setParameter("id", id);
			final T item = getSession().find(type, id);
			getSession().remove(item);

			return null;
		});
	}

	/** {@inheritDoc} */
	@Override
	public void saveDataStorage() {
		bindSession();

		getSession().flush();
	}

	/** {@inheritDoc} */
	@Override
	public void clearDataStorage() {
		Logger.warn("Clearing database not supported yet");
	}

	@Override
	public void evictCaches() {
		bindSession();

		getSession().clear();
	}

	/** {@inheritDoc} */
	@Override
	public <T extends Item> void initItem(final T item) {
		for (final Field field : ClassUtil.getFieldsWithAnnotation(item.getClass(), Property.class)) {
			Object instanceValue = ClassUtil.getField(item, field.getName(), true);

			if (instanceValue == null) {
				if (field.getType().isAssignableFrom(Set.class)) {
					instanceValue = new HashSet<>();
				} else if (field.getType().isAssignableFrom(List.class)
						|| field.getType().isAssignableFrom(Collection.class)) {
					instanceValue = new ArrayList<>();
				} else if (field.getType().isAssignableFrom(Map.class)) {
					instanceValue = new HashMap<>();
				}

				if (instanceValue != null) {
					ClassUtil.setField(item, field.getName(), instanceValue);
				}
			}
		}
	}

	/** {@inheritDoc} */
	@Override
	public <T extends Item> void detach(final List<T> items) {
		bindSession();

		for (final T item : items) {
			getSession().detach(item);
		}
	}

	/** {@inheritDoc} */
	@Override
	public <T extends Item> boolean isAttached(final T item) {
		bindSession();

		return getSession().contains(item);
	}

	@Override
	public <T extends Item> Optional<String> getTableName(Class<T> itemType) {
		bindSession();

		return transactionService.execute(() -> {
			SessionImpl session = (SessionImpl) getSession();

			final Optional<T> example = ClassUtil.instantiate(itemType);
			final EntityPersister persister = session.getEntityPersister(null, example.get());

			if (persister instanceof AbstractEntityPersister) {
				AbstractEntityPersister persisterImpl = (AbstractEntityPersister) persister;

				String tableName = persisterImpl.getTableName();
				String rootTableName = persisterImpl.getRootTableName();

				return Optional.of(tableName);
			} else {
				throw new RuntimeException("Unexpected persister type; a subtype of AbstractEntityPersister expected.");
			}
		});
	}

	public Session getSession() {
		final EntityManagerHolder holder = ((EntityManagerHolder) TransactionSynchronizationManager
				.getResource(entityManagerFactory));

		if (holder != null) {
			if (Logger.isLogLevelEnabled(LogLevel.DEBUG)) {
				getSessionFactory().getStatistics().setStatisticsEnabled(true);
			}

			return holder.getEntityManager().unwrap(Session.class);
		}

		throw new IllegalStateException("Could not fetch persistence entity manager");
	}

	protected void bindSession() {
		if (!TransactionSynchronizationManager.hasResource(entityManagerFactory)) {
			TransactionSynchronizationManager.bindResource(entityManagerFactory,
					new EntityManagerHolder(entityManagerFactory.createEntityManager()));
		}
	}

	/** {@inheritDoc} */
	@Override
	public void unbindSession() {
		if (TransactionSynchronizationManager.hasResource(entityManagerFactory)) {
			final EntityManagerHolder emHolder = (EntityManagerHolder) TransactionSynchronizationManager
					.unbindResource(entityManagerFactory);
			EntityManagerFactoryUtils.closeEntityManager(emHolder.getEntityManager());
		} else {
			throw new IllegalStateException("No entitiy manager factory found");
		}
	}

	/**
	 * <p>
	 * Getter for the field <code>entityManagerFactory</code>.
	 * </p>
	 *
	 * @return a {@link javax.persistence.EntityManagerFactory} object.
	 */
	public EntityManagerFactory getEntityManagerFactory() {
		return entityManagerFactory;
	}

	/**
	 * <p>
	 * getSessionFactory.
	 * </p>
	 *
	 * @return a {@link SessionFactory} object.
	 */
	public SessionFactory getSessionFactory() {
		return entityManagerFactory.unwrap(SessionFactory.class);
	}

	public Statistics getStatistics() {
		return getSessionFactory().getStatistics();
	}

}
