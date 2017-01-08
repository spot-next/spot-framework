package at.spot.jfly.demo;

import at.spot.jfly.Body;
import at.spot.jfly.Head;
import at.spot.jfly.JFlyApplication;
import at.spot.jfly.Script;
import at.spot.jfly.Stylesheet;
import at.spot.jfly.event.JsEvent;
import at.spot.jfly.style.ButtonStyle;
import at.spot.jfly.ui.Button;
import at.spot.jfly.ui.NavBar;
import j2html.TagCreator;

public class DemoApplication extends JFlyApplication {

	@Override
	protected Head createHeader() {
		final Head head = new Head().title("Hello world");
		head.stylesheet(new Stylesheet("https://maxcdn.bootstrapcdn.com/bootstrap/3.3.7/css/bootstrap.min.css"));
		head.script(new Script("https://maxcdn.bootstrapcdn.com/bootstrap/3.3.7/js/bootstrap.min.js"));
		head.script(new Script("http://zeptojs.com/zepto.min.js"));
		head.script(new Script("/script/jfly.js"));

		return head;
	}

	@Override
	protected Body createBody() {
		final Body body = new Body().addChild(new NavBar());

		final Button button = new Button("Say hello!").style(ButtonStyle.Success);
		button.onEvent(JsEvent.click, e -> {
			System.out.println("Click from: " + e.getSource().uuid());

			button.caption("hello world");

			body.addChild(TagCreator.h1("test"));
		});

		body.addChild(button);

		return body;
	}
}
