import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Writes tools/dungeon_entrances.json from RuneLite's own world-map dungeon list, so the
 * track sweep can point players at a surface entrance for tracks that play underground.
 *
 * <p>The enum is package-private, hence reflection; this is a one-off tool, not plugin code.
 * Run with the RuneLite client jar and its dependencies on the classpath, for example:
 * <pre>
 *   ./gradlew -q printTestClasspath   (or copy the path from an IDE)
 *   java -cp "&lt;that classpath&gt;" tools/DumpDungeonEntrances.java tools/dungeon_entrances.json
 * </pre>
 */
public class DumpDungeonEntrances
{
	public static void main(String[] args) throws Exception
	{
		Class<?> c = Class.forName("net.runelite.client.plugins.worldmap.DungeonLocation");
		Object[] values = c.getEnumConstants();
		Method tooltip = c.getDeclaredMethod("getTooltip");
		tooltip.setAccessible(true);
		Method location = c.getDeclaredMethod("getLocation");
		location.setAccessible(true);
		StringBuilder sb = new StringBuilder("[\n");
		for (int i = 0; i < values.length; i++)
		{
			net.runelite.api.coords.WorldPoint p = (net.runelite.api.coords.WorldPoint) location.invoke(values[i]);
			String name = ((String) tooltip.invoke(values[i])).replace("\"", "'");
			sb.append(" {\"name\": \"").append(name).append("\", \"x\": ").append(p.getX())
				.append(", \"y\": ").append(p.getY()).append(", \"plane\": ").append(p.getPlane()).append('}')
				.append(i + 1 < values.length ? ",\n" : "\n");
		}
		sb.append("]\n");
		Files.write(Paths.get(args[0]), sb.toString().getBytes(StandardCharsets.UTF_8));
		System.out.println("wrote " + values.length + " entrances to " + args[0]);
	}
}
