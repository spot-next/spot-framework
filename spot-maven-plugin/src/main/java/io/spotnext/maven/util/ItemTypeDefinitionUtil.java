package io.spotnext.maven.util;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.sonatype.plexus.build.incremental.BuildContext;

import io.spotnext.infrastructure.constants.InfrastructureConstants;
import io.spotnext.infrastructure.maven.TypeDefinitions;
import io.spotnext.infrastructure.maven.xml.AtomicType;
import io.spotnext.infrastructure.maven.xml.BaseType;
import io.spotnext.infrastructure.maven.xml.BeanType;
import io.spotnext.infrastructure.maven.xml.CollectionType;
import io.spotnext.infrastructure.maven.xml.EnumType;
import io.spotnext.infrastructure.maven.xml.EnumValue;
import io.spotnext.infrastructure.maven.xml.ItemType;
import io.spotnext.infrastructure.maven.xml.JavaType.Properties;
import io.spotnext.infrastructure.maven.xml.MapType;
import io.spotnext.infrastructure.maven.xml.Property;
import io.spotnext.infrastructure.maven.xml.RelationType;
import io.spotnext.infrastructure.maven.xml.Types;
import io.spotnext.infrastructure.type.Item;
import io.spotnext.maven.exception.IllegalItemTypeDefinitionException;
import io.spotnext.support.util.FileUtils;
import io.spotnext.support.util.ValidationUtil;

/**
 * <p>
 * ItemTypeDefinitionUtil class.
 * </p>
 *
 * @since 1.0
 */
public class ItemTypeDefinitionUtil {
	protected MavenProject project;
	protected ArtifactRepository localRepository;
	private BuildContext buildContext;
	protected Log log;

	/**
	 * <p>
	 * Constructor for ItemTypeDefinitionUtil.
	 * </p>
	 *
	 * @param project         a {@link MavenProject} object.
	 * @param localRepository a {@link ArtifactRepository} object.
	 * @param buildContext    the plexus utils build context for Eclipse intergration
	 * @param log             a {@link org.apache.maven.plugin.logging.Log} object.
	 */
	public ItemTypeDefinitionUtil(final MavenProject project, final ArtifactRepository localRepository, BuildContext buildContext, final Log log) {
		this.project = project;
		this.localRepository = localRepository;
		this.buildContext = buildContext;
		this.log = log;
	}

	/**
	 * Fetches all itemtype definition files available in the maven dependency
	 * hierarchy.
	 *
	 * @return a {@link io.spotnext.infrastructure.maven.TypeDefinitions}
	 *         object.
	 * @throws io.spotnext.maven.exception.IllegalItemTypeDefinitionException if
	 *         there is a problem with resolving the types
	 */
	public TypeDefinitions fetchItemTypeDefinitions() throws IllegalItemTypeDefinitionException {
		final List<InputStream> definitionsFiles = findItemTypeDefinitions();
		TypeDefinitions itemTypesDefinitions;
		itemTypesDefinitions = aggregateTypeDefninitions(definitionsFiles);

		return itemTypesDefinitions;
	}

	/**
	 * Search all dependencies and the current project's resource folders to get
	 * item type definition files.
	 *
	 * @return a {@link java.util.List} object.
	 * @throws io.spotnext.maven.exception.IllegalItemTypeDefinitionException in
	 *         case an artifact can't be read from the file system
	 */
	protected List<InputStream> findItemTypeDefinitions() throws IllegalItemTypeDefinitionException {
		final Map<String, InputStream> definitionFiles = new LinkedHashMap<>();

		// get all dependencies and iterate over the target/classes folder
		final Set<Artifact> files = project.getDependencyArtifacts();

		for (final Artifact a : files) {
			log.info(String.format("Scanning %s for item types ...", a));

			try {
				final File file = MavenUtil.getArtiactFile(localRepository, a);

				// final File file = deps.get(0).getFile();
				log.info(String.format("Resolved artfact %s: %s", a.getArtifactId(), file.getAbsolutePath()));

				for (final File f : FileUtils.getFiles(file.getAbsolutePath(), null)) {
					if (f.getName().endsWith(".jar")) {
						final List<String> jarContent = FileUtils.getFileListFromJar(f.getAbsolutePath());
						for (final String c : jarContent) {
							if (isItemTypeDefinitionFile(c)) {
								final String fileName = f.getAbsolutePath() + "/" + c;

								if (definitionFiles.get(fileName) == null) {
									definitionFiles.put(fileName,
											FileUtils.readFileFromZipFile(f.getAbsolutePath(), c));
								}
							}
						}
					} else {
						if (isItemTypeDefinitionFile(f.getName())) {
							if (definitionFiles.get(f.getAbsolutePath()) == null) {
								definitionFiles.put(file.getAbsolutePath(), FileUtils.readFile(f));
							}
						}
					}
				}
			} catch (final IOException e) {
				throw new IllegalItemTypeDefinitionException(
						String.format("Can't read artifact file for artifact %s.", a), e);
			}
		}

		// get all resource files in the current project
		for (final Resource r : (List<Resource>) project.getResources()) {
			final List<File> projectFiles = FileUtils.getFiles(r.getDirectory(), null);

			for (final File f : projectFiles) {
				if (isItemTypeDefinitionFile(f.getName())) {
					try {
						if (definitionFiles.get(f.getAbsolutePath()) == null) {
							definitionFiles.put(f.getAbsolutePath(), FileUtils.readFile(f));
						}
					} catch (final FileNotFoundException e) {
						throw new IllegalItemTypeDefinitionException("Could not scan for item types.", e);
					}
				}
			}
		}

		log.info(String.format("Found XML definitions: %s", StringUtils.join(definitionFiles, ", ")));

		return new ArrayList<>(definitionFiles.values());
	}

	/**
	 * <p>
	 * populateTypeDefinition.
	 * </p>
	 *
	 * @param source     a {@link java.util.List} object.
	 * @param target     a {@link java.util.Map} object.
	 * @param mergeTypes a boolean.
	 * @param            <T> a T object.
	 * @throws io.spotnext.maven.exception.IllegalItemTypeDefinitionException if
	 *         there is a problem merging type definitions.
	 */
	protected <T extends BaseType> void populateTypeDefinition(final List<T> source, final Map<String, T> target,
			final boolean mergeTypes) throws IllegalItemTypeDefinitionException {

		for (final T def : source) {
			final T existing = target.get(def.getName());

			if (existing == null) {
				target.put(def.getName(), def);
			} else {
				if (mergeTypes) {
					log.warn(String.format("Redefinition of type '%s' not yet supported",
							def.getClass().getSimpleName()));
				} else {
					throw new IllegalItemTypeDefinitionException(
							String.format("Duplicate type definition '%s'", def.getName()));
				}
			}
		}
	}

	/**
	 * Aggregate all item type definitions of all definition files.
	 *
	 * @param definitions a {@link java.util.List} object.
	 * @return a {@link io.spotnext.infrastructure.maven.TypeDefinitions}
	 *         object.
	 * @throws io.spotnext.maven.exception.IllegalItemTypeDefinitionException if
	 *         there is a problem merging type definitions.
	 */
	protected TypeDefinitions aggregateTypeDefninitions(final List<InputStream> definitions)
			throws IllegalItemTypeDefinitionException {

		final TypeDefinitions typeDefinitions = new TypeDefinitions();

		final Map<String, ItemType> itemDefs = typeDefinitions.getItemTypes();
		final Map<String, EnumType> enumsDefs = typeDefinitions.getEnumTypes();
		final Map<String, BeanType> beanDefs = typeDefinitions.getBeanTypes();
		final Map<String, AtomicType> atomicDefs = typeDefinitions.getAtomicTypes();
		final Map<String, CollectionType> collectionDefs = typeDefinitions.getCollectionTypes();
		final Map<String, MapType> mapDefs = typeDefinitions.getMapTypes();
		final Map<String, RelationType> relationDefs = typeDefinitions.getRelationTypes();

		{// add base item type, just to make it referenceable
			final ItemType itemType = new ItemType();
			itemType.setName(Item.class.getSimpleName());
			itemType.setPackage(Item.class.getPackage().getName());
			itemType.setAbstract(true);
			itemType.setTypeCode(StringUtils.lowerCase(itemType.getName()));

			itemDefs.put(itemType.getName(), itemType);
		}

		// iterate over all itemtypes.xml files
		for (final InputStream defFile : definitions) {
			final Types typesDefs = loadTypeDefinition(defFile);

			ValidationUtil.validateNotNull("Could not load type definitions", typesDefs);

			// these types are only allowed to be defined once
			populateTypeDefinition(typesDefs.getAtomic(), atomicDefs, false);
			populateTypeDefinition(typesDefs.getCollection(), collectionDefs, false);
			populateTypeDefinition(typesDefs.getMap(), mapDefs, false);
			populateTypeDefinition(typesDefs.getRelation(), relationDefs, false);

			// handle enums
			for (final EnumType enumDef : typesDefs.getEnum()) {
				final EnumType existingEnum = enumsDefs.get(enumDef.getName());

				if (existingEnum != null) {
					for (final EnumValue v : enumDef.getValue()) {
						final boolean exists = existingEnum.getValue().stream()
								.filter((i) -> StringUtils.equals(i.getCode(), v.getCode())).findAny().isPresent();

						if (!exists) {
							existingEnum.getValue().add(v);
						}
					}
				} else {
					enumsDefs.put(enumDef.getName(), enumDef);
				}
			}

			// handle bean types
			for (final BeanType typeDef : typesDefs.getBean()) {
				BeanType existingType = beanDefs.get(typeDef.getName());

				if (existingType == null) {
					existingType = typeDef;
					beanDefs.put(existingType.getName(), existingType);
				} else {
					if (typeDef.getProperties() != null
							&& CollectionUtils.isNotEmpty(typeDef.getProperties().getProperty())) {
						for (final Property p : typeDef.getProperties().getProperty()) {
							boolean add = true;

							if (existingType.getProperties() != null) {
								final Optional<Property> existingProp = existingType.getProperties().getProperty()
										.stream().filter((prop) -> StringUtils.equals(prop.getName(), p.getName()))
										.findFirst();

								add = !existingProp.isPresent();
							} else {
								existingType.setProperties(new Properties());
							}

							if (add) {
								existingType.getProperties().getProperty().add(p);
							}
						}
					}
				}
			}

			// handle item types
			for (final ItemType typeDef : typesDefs.getType()) {
				ItemType existingType = itemDefs.get(typeDef.getName());

				if (existingType == null) {
					existingType = typeDef;
					itemDefs.put(existingType.getName(), existingType);
				} else {
					// if (existingType.isAbstract() == null) {
					// existingType.setAbstract(typeDef.isAbstract());
					// }

					// if (StringUtils.isBlank(existingType.getPackage())) {
					// existingType.setPackage(typeDef.getPackage());
					// }
					// if (StringUtils.isBlank(existingType.getTypeCode())) {
					// existingType.setPackage(typeDef.getTypeCode());
					// }
					//
					// if (StringUtils.isBlank(existingType.getExtends())) {
					// existingType.setExtends(typeDef.getExtends());
					// }
					if (typeDef.getProperties() != null
							&& CollectionUtils.isNotEmpty(typeDef.getProperties().getProperty())) {
						for (final Property p : typeDef.getProperties().getProperty()) {
							boolean add = true;

							if (existingType.getProperties() != null) {
								final Optional<Property> existingProp = existingType.getProperties().getProperty()
										.stream().filter((prop) -> StringUtils.equals(prop.getName(), p.getName()))
										.findFirst();

								add = !existingProp.isPresent();
							} else {
								existingType.setProperties(new Properties());
							}

							if (add) {
								existingType.getProperties().getProperty().add(p);
							}
						}
					}
				}
			}
		}

		// sanitize type codes so there are no nulls -> just use the lowercase
		// type name as fallback
		typeDefinitions.getItemTypes().values().stream().forEach(i -> {
			if (StringUtils.isBlank(i.getTypeCode())) {
				i.setTypeCode(StringUtils.lowerCase(i.getName()));
			} else {
				i.setTypeCode(StringUtils.lowerCase(i.getTypeCode()));
			}
		});

		return typeDefinitions;
	}

	/**
	 * Parses a given XML item type definition file and unmarshals it to a
	 * {@link io.spotnext.infrastructure.maven.xml.Types} object.
	 *
	 * @param file a {@link java.io.InputStream} object.
	 * @return a {@link io.spotnext.infrastructure.maven.xml.Types} object.
	 */
	protected Types loadTypeDefinition(final InputStream file) {
		Types typeDef = null;

		try {
			final JAXBContext context = JAXBContext.newInstance(Types.class);
			final Unmarshaller jaxb = context.createUnmarshaller();
			// jaxb.setProperty(Marshaller.JAXB_NO_NAMESPACE_SCHEMA_LOCATION,
			// "itemtypes.xsd");

			typeDef = (Types) jaxb.unmarshal(file);
		} catch (final Exception e) {
			Throwable rootCause = ExceptionUtils.getRootCause(e);

			log.error(rootCause.getMessage());
		}

		return typeDef;
	}

	/**
	 * Checks if the given file's name matches the item type definition filename
	 * pattern.
	 *
	 * @param fileName a {@link java.lang.String} object.
	 * @return a boolean.
	 */
	protected boolean isItemTypeDefinitionFile(final String fileName) {
		// ignore merged itemtypes files.
		if (StringUtils.equals(InfrastructureConstants.MERGED_INDEXED_ITEMTYPES_FILENAME, fileName)
				|| StringUtils.equals(InfrastructureConstants.MERGED_ITEMTYPES_FILENAME, fileName)) {

			return false;
		}

		return StringUtils.endsWith(fileName, "-itemtypes.xml");
	}

	/**
	 * Store merged item type definitions in the build folder.
	 *
	 * @param itemTypesDefinitions     a
	 *                                 {@link io.spotnext.infrastructure.maven.TypeDefinitions}
	 *                                 object.
	 * @param targetResourcesDirectory a {@link java.io.File} object.
	 */
	public void saveTypeDefinitions(final TypeDefinitions itemTypesDefinitions, final File targetResourcesDirectory) {
		if (!targetResourcesDirectory.exists() && !targetResourcesDirectory.mkdir()) {
			log.error("Could not create target output directory for merged item types file.");
		}

		// map-like indexed merged output file
		try {
			final JAXBContext context = JAXBContext.newInstance(TypeDefinitions.class);
			final Marshaller jaxb = context.createMarshaller();
			jaxb.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);

			File file = new File(targetResourcesDirectory, InfrastructureConstants.MERGED_INDEXED_ITEMTYPES_FILENAME);
			jaxb.marshal(itemTypesDefinitions, file);
			buildContext.refresh(file);
		} catch (final JAXBException e) {
			log.error(e);
		}

		// merged original output file
		final Types outputTypes = new Types();
		outputTypes.getEnum().addAll(itemTypesDefinitions.getEnumTypes().values());
		outputTypes.getType().addAll(itemTypesDefinitions.getItemTypes().values());

		try {
			final JAXBContext context = JAXBContext.newInstance(Types.class);
			final Marshaller jaxb = context.createMarshaller();
			jaxb.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);

			File file = new File(targetResourcesDirectory, InfrastructureConstants.MERGED_ITEMTYPES_FILENAME);
			jaxb.marshal(outputTypes, file);
			buildContext.refresh(file);
		} catch (final JAXBException e) {
			log.error(e);
		}
	}
}
