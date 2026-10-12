package de.zbidi.jobtracker.jobsource;

import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

/**
 * Turns a portal's HTML job text into readable plain text for the AI: blocks become lines, list items bullets,
 * entities are decoded, other tags dropped. Not a general HTML parser, just enough for job descriptions.
 */
final class HtmlToText {

	private static final Pattern LIST_ITEM = Pattern.compile("(?i)<li[^>]*>");
	private static final Pattern BLOCK_END = Pattern.compile("(?i)</(p|div|h[1-6]|tr)>|<br\\s*/?>");
	private static final Pattern TAG = Pattern.compile("<[^>]+>");
	private static final Pattern SPACES = Pattern.compile("[ \\t\\x0B\\f\\r]+");
	private static final Pattern MANY_NEWLINES = Pattern.compile("\n{3,}");
	/** A list follows its heading directly: no blank line before a bullet. */
	private static final Pattern BLANK_BEFORE_BULLET = Pattern.compile("\n{2,}- ");

	private HtmlToText() {
	}

	static String convert(@Nullable String html) {
		if (html == null) {
			return "";
		}
		String text = LIST_ITEM.matcher(html).replaceAll("\n- ");
		text = BLOCK_END.matcher(text).replaceAll("\n");
		text = TAG.matcher(text).replaceAll("");
		text = decodeEntities(text);
		text = Arrays.stream(text.split("\n", -1))
				.map(line -> SPACES.matcher(line).replaceAll(" ").strip())
				.collect(Collectors.joining("\n"));
		text = MANY_NEWLINES.matcher(text).replaceAll("\n\n");
		return BLANK_BEFORE_BULLET.matcher(text).replaceAll("\n- ").strip();
	}

	private static String decodeEntities(String text) {
		return text.replace("&nbsp;", " ")
				.replace("&lt;", "<")
				.replace("&gt;", ">")
				.replace("&quot;", "\"")
				.replace("&#39;", "'")
				.replace("&apos;", "'")
				.replace("&amp;", "&"); // last: "&amp;lt;" must become "&lt;", not "<"
	}

}
