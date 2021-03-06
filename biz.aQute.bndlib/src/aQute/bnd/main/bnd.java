package aQute.bnd.main;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.jar.*;
import java.util.regex.*;
import java.util.zip.*;

import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.*;
import javax.xml.transform.stream.*;
import javax.xml.xpath.*;

import org.w3c.dom.*;

import aQute.bnd.build.*;
import aQute.bnd.maven.*;
import aQute.bnd.service.*;
import aQute.bnd.service.action.*;
import aQute.bnd.test.*;
import aQute.lib.deployer.*;
import aQute.lib.jardiff.*;
import aQute.lib.osgi.*;
import aQute.lib.osgi.eclipse.*;
import aQute.lib.tag.*;
import aQute.libg.generics.*;
import aQute.libg.version.*;

/**
 * Utility to make bundles.
 * 
 * TODO Add Javadoc comment for this type.
 * 
 * @version $Revision: 1.14 $
 */
public class bnd extends Processor {
	PrintStream out = System.out;
	static boolean exceptions = false;

	static boolean failok = false;
	private Project project;

	public static void main(String args[]) {
		bnd main = new bnd();
		try {
			main.run(args);
			if (bnd.failok)
				return;

			System.exit(main.getErrors().size());

		} catch (Exception e) {
			System.err.println("Software error occurred " + e);
			if (exceptions)
				e.printStackTrace();
		}
		System.exit(-1);
	}

	void run(String[] args) throws Exception {
		int cnt = 0;
		for (int i = 0; i < args.length; i++) {
			if ("-failok".equals(args[i])) {
				failok = true;
			} else if ("-exceptions".equals(args[i])) {
				exceptions = true;
			} else if ("-trace".equals(args[i])) {
				setTrace(true);
			} else if ("-pedantic".equals(args[i])) {
				setPedantic(true);
			} else if ("-base".equals(args[i])) {
				setBase(new File(args[++i]).getAbsoluteFile());
				if (!getBase().isDirectory()) {
					out.println("-base must be a valid directory");
				}
			} else if ("wrap".equals(args[i])) {
				cnt++;
				doWrap(args, ++i);
				break;
			} else if ("print".equals(args[i])) {
				cnt++;
				doPrint(args, ++i);
				break;
			} else if ("graph".equals(args[i])) {
				cnt++;
				doDot(args, ++i);
				break;
			} else if ("release".equals(args[i])) {
				cnt++;
				doRelease(args, ++i);
				break;
			} else if ("debug".equals(args[i])) {
				cnt++;
				debug(args, ++i);
				break;
			} else if ("deliverables".equals(args[i])) {
				cnt++;
				deliverables(args, ++i);
				break;
			} else if ("view".equals(args[i])) {
				cnt++;
				doView(args, ++i);
				break;
			} else if ("build".equals(args[i])) {
				cnt++;
				doBuild(args, ++i);
				break;
			} else if ("extract".equals(args[i])) {
				cnt++;
				doExtract(args, ++i);
				break;
			} else if ("patch".equals(args[i])) {
				cnt++;
				patch(args, ++i);
				break;
			} else if ("runtests".equals(args[i])) {
				cnt++;
				runtests(args, ++i);
				break;
			} else if ("xref".equals(args[i])) {
				cnt++;
				doXref(args, ++i);
				break;
			} else if ("eclipse".equals(args[i])) {
				cnt++;
				doEclipse(args, ++i);
				break;
			} else if ("repo".equals(args[i])) {
				cnt++;
				repo(args, ++i);
				break;
			} else if ("diff".equals(args[i])) {
				cnt++;
				doDiff(args, ++i);
				break;
			} else if ("test".equals(args[i])) {
				cnt++;
				test(args, ++i);
				break;
			} else if ("help".equals(args[i])) {
				cnt++;
				doHelp(args, ++i);
				break;
			} else if ("macro".equals(args[i])) {
				cnt++;
				doMacro(args, ++i);
				break;
			} else {
				Project p = getProject();
				if ( p != null ) {
					 Action a = p.getActions().get(args[i]);
					 System.out.println("Found action in " + p.getActions() + " " + a);
					 if ( a != null ) {
						 cnt++;
						 // parse args
						 a.execute(p, args[i++]);
						 getInfo(p);
						 break;
					 }
				} 
				
				
				
				cnt++;
				String path = args[i];
				if (path.startsWith("-")) {
					doHelp(args, i);
					error("Invalid option on commandline: " + args[i]);
					break;
				} else {
					if (path.endsWith(Constants.DEFAULT_BND_EXTENSION))
						doBuild(new File(path), new File[0], new File[0], null,
								"", new File(path).getParentFile(), 0,
								new HashSet<File>());
					else if (path.endsWith(Constants.DEFAULT_JAR_EXTENSION)
							|| path.endsWith(Constants.DEFAULT_BAR_EXTENSION))
						doPrint(path, -1);
					else {
						try {
							i = doMacro(args, i);
						} catch (Throwable t) {
							t.printStackTrace();
							doHelp(args, i);
							error("Invalid commandline: " + args[i]);
							break;
						}
					}
				}
			}
		}

		if (cnt == 0) {
			File f = new File("bnd.bnd");
			if (f.exists()) {
				doBuild(f, new File[0], new File[0], null, "", f
						.getParentFile(), 0, new HashSet<File>());
			} else {
				doHelp();
				error("No files on commandline");
			}
		}
		int n = 1;
		switch (getErrors().size()) {
		case 0:
			// System.err.println("No errors");
			break;
		case 1:
			System.err.println("One error");
			break;
		default:
			System.err.println(getErrors().size() + " errors");
		}
		for (String msg : getErrors()) {
			System.err.println(n++ + " : " + msg);
		}
		n = 1;
		switch (getWarnings().size()) {
		case 0:
			// System.err.println("No warnings");
			break;
		case 1:
			System.err.println("One warning");
			break;
		default:
			System.err.println(getWarnings().size() + " warnings");
		}
		for (String msg : getWarnings()) {
			System.err.println(n++ + " : " + msg);
		}
	}

	private void deliverables(String[] args, int i) throws Exception {
		Project project = getProject();
		long start = System.currentTimeMillis();
		Collection<Project> projects = project.getWorkspace().getAllProjects();
		List<Container> containers = new ArrayList<Container>();
		for (Project p : projects) {
			containers.addAll(p.getDeliverables());
		}
		long duration = System.currentTimeMillis() - start;
		System.out.println("Took " + duration + " ms");

		for (Container c : containers) {
			Version v = new Version(c.getVersion());
			System.out.printf("%-40s %d.%d.%d %s\n", c.getBundleSymbolicName(),
					v.getMajor(), v.getMinor(), v.getMicro(), c.getFile());
		}

	}

	private int doMacro(String[] args, int i) throws Exception {
		String result;
		for (; i < args.length; i++) {
			String cmd = args[i];
			cmd = cmd.replaceAll("@\\{([^}])\\}", "\\${$1}");
			cmd = cmd.replaceAll(":", ";");
			cmd = cmd.replaceAll("[^$](.*)", "\\${$0}");
			result = getProject().getReplacer().process(cmd);
			if (result != null && result.length() != 0) {
				Collection<String> parts = split(result);
				for (String s : parts) {
					out.println(s);
				}
			} else
				out.println("No result for " + cmd);

		}
		return i;
	}

	private void doRelease(String[] args, int i) throws Exception {
		Project project = getProject();
		project.release(false);
		getInfo(project);
	}

	/**
	 * Cross reference every class in the jar pom to the files it references
	 * 
	 * @param args
	 * @param i
	 */

	private void doXref(String[] args, int i) {
		for (; i < args.length; i++) {
			try {
				File file = new File(args[i]);
				Jar jar = new Jar(file.getName(), file);
				try {
					for (Map.Entry<String, Resource> entry : jar.getResources()
							.entrySet()) {
						String key = entry.getKey();
						Resource r = entry.getValue();
						if (key.endsWith(".class")) {
							InputStream in = r.openInputStream();
							Clazz clazz = new Clazz(key, r);
							out.print(key);
							Set<String> xref = clazz.parseClassFile();
							in.close();
							for (String element : xref) {
								out.print("\t");
								out.print(element);
							}
							out.println();
						}
					}
				} finally {
					jar.close();
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

	}

	private void doEclipse(String[] args, int i) throws Exception {
		File dir = new File("").getAbsoluteFile();
		if (args.length == i)
			doEclipse(dir);
		else {
			for (; i < args.length; i++) {
				doEclipse(new File(dir, args[i]));
			}
		}
	}

	private void doEclipse(File file) throws Exception {
		if (!file.isDirectory())
			error("Eclipse requires a path to a directory: "
					+ file.getAbsolutePath());
		else {
			File cp = new File(file, ".classpath");
			if (!cp.exists()) {
				error("Cannot find .classpath in project directory: "
						+ file.getAbsolutePath());
			} else {
				EclipseClasspath eclipse = new EclipseClasspath(this, file
						.getParentFile(), file);
				out.println("Classpath    " + eclipse.getClasspath());
				out.println("Dependents   " + eclipse.getDependents());
				out.println("Sourcepath   " + eclipse.getSourcepath());
				out.println("Output       " + eclipse.getOutput());
				out.println();
			}
		}

	}

	final static int BUILD_SOURCES = 1;
	final static int BUILD_POM = 2;
	final static int BUILD_FORCE = 4;

	private void doBuild(String[] args, int i) throws Exception {
		File classpath[] = new File[0];
		File workspace = null;
		File sourcepath[] = new File[0];
		File output = null;
		String eclipse = "";
		int options = 0;

		for (; i < args.length; i++) {
			if ("-workspace".startsWith(args[i])) {
				workspace = new File(args[++i]);
			} else if ("-classpath".startsWith(args[i])) {
				classpath = getClasspath(args[++i]);
			} else if ("-sourcepath".startsWith(args[i])) {
				String arg = args[++i];
				String spaces[] = arg.split("\\s*,\\s*");
				sourcepath = new File[spaces.length];
				for (int j = 0; j < spaces.length; j++) {
					File f = new File(spaces[j]);
					if (!f.exists())
						error("No such sourcepath entry: "
								+ f.getAbsolutePath());
					sourcepath[j] = f;
				}
			} else if ("-eclipse".startsWith(args[i])) {
				eclipse = args[++i];
			} else if ("-noeclipse".startsWith(args[i])) {
				eclipse = null;
			} else if ("-output".startsWith(args[i])) {
				output = new File(args[++i]);
			} else if ("-sources".startsWith(args[i])) {
				options |= BUILD_SOURCES;
			} else if ("-pom".startsWith(args[i])) {
				options |= BUILD_POM;
			} else if ("-force".startsWith(args[i])) {
				options |= BUILD_FORCE;
			} else {
				if (args[i].startsWith("-"))
					error("Invalid option for bnd: " + args[i]);
				else {
					File properties = new File(args[i]);

					if (!properties.exists())
						error("Cannot find bnd pom: " + args[i]);
					else {
						if (workspace == null)
							workspace = properties.getParentFile();

						doBuild(properties, classpath, sourcepath, output,
								eclipse, workspace, options,
								new HashSet<File>());
					}
					output = null;
				}
			}
		}

	}

	private File[] getClasspath(String string) {
		String spaces[] = string.split("\\s*,\\s*");
		File classpath[] = new File[spaces.length];
		for (int j = 0; j < spaces.length; j++) {
			File f = new File(spaces[j]);
			if (!f.exists())
				error("No such classpath entry: " + f.getAbsolutePath());
			classpath[j] = f;
		}
		return classpath;
	}

	private void doBuild(File properties, File classpath[], File sourcepath[],
			File output, String eclipse, File workspace, int options,
			Set<File> building) throws Exception {

		properties = properties.getAbsoluteFile();
		if (building.contains(properties)) {
			error("Circular dependency in pre build " + properties);
			return;
		}
		building.add(properties);

		Builder builder = new Builder();
		try {
			builder.setPedantic(isPedantic());
			builder.setProperties(properties);

			if (output == null) {
				String out = builder.getProperty("-output");
				if (out != null) {
					output = getFile(properties.getParentFile(), out);
					if (!output.getName().endsWith(".jar"))
						output.mkdirs();
				} else
					output = properties.getAbsoluteFile().getParentFile();
			}

			String prebuild = builder.getProperty("-prebuild");
			if (prebuild != null)
				prebuild(prebuild, properties.getParentFile(), classpath,
						sourcepath, output, eclipse, workspace, options,
						building);

			doEclipse(builder, properties, classpath, sourcepath, eclipse,
					workspace);

			if ((options & BUILD_SOURCES) != 0)
				builder.getProperties().setProperty("-sources", "true");

			if (failok)
				builder.setProperty(Analyzer.FAIL_OK, "true");
			Jar jar = builder.build();
			getInfo(builder);
			if (getErrors().size() > 0 && !failok)
				return;

			String name = builder.getBsn() + DEFAULT_JAR_EXTENSION;

			if (output.isDirectory())
				output = new File(output, name);

			output.getParentFile().mkdirs();

			if ((options & BUILD_POM) != 0) {
				Resource r = new PomResource(jar.getManifest());
				jar.putResource("pom.xml", r);
				String path = output.getName().replaceAll("\\.jar$", ".pom");
				if (path.equals(output.getName()))
					path = output.getName() + ".pom";
				File pom = new File(output.getParentFile(), path);
				OutputStream out = new FileOutputStream(pom);
				try {
					r.write(out);
				} finally {
					out.close();
				}
			}
			jar.setName(output.getName());

			String msg = "";
			if (!output.exists() || output.lastModified() <= jar.lastModified()
					|| (options & BUILD_FORCE) != 0) {
				jar.write(output);
			} else {
				msg = "(not modified)";
			}
			statistics(jar, output, msg);
		} finally {
			builder.close();
		}
	}

	private void prebuild(String prebuild, File base, File[] classpath,
			File[] sourcepath, File output, String eclipse2, File workspace,
			int options, Set<File> building) throws Exception {

		// Force the output a directory
		if (output.isFile())
			output = output.getParentFile();

		Collection<String> parts = Processor.split(prebuild);
		for (String part : parts) {
			File f = new File(part);
			if (!f.exists())
				f = new File(base, part);
			if (!f.exists()) {
				error("Trying to build a non-existent pom: " + parts);
				continue;
			}
			try {
				doBuild(f, classpath, sourcepath, output, eclipse2, workspace,
						options, building);
			} catch (Exception e) {
				error("Trying to build: " + part + " " + e);
			}
		}
	}

	private void statistics(Jar jar, File output, String msg) {
		out.println(jar.getName() + " " + jar.getResources().size() + " "
				+ output.length() + msg);
	}

	/**
	 * @param properties
	 * @param classpath
	 * @param eclipse
	 * @return
	 * @throws IOException
	 */
	void doEclipse(Builder builder, File properties, File[] classpath,
			File sourcepath[], String eclipse, File workspace)
			throws IOException {
		if (eclipse != null) {
			File project = new File(workspace, eclipse).getAbsoluteFile();
			if (project.exists() && project.isDirectory()) {
				try {

					EclipseClasspath path = new EclipseClasspath(this, project
							.getParentFile(), project);
					List<File> newClasspath = Create.copy(Arrays
							.asList(classpath));
					newClasspath.addAll(path.getClasspath());
					classpath = (File[]) newClasspath.toArray(classpath);

					List<File> newSourcepath = Create.copy(Arrays
							.asList(sourcepath));
					newSourcepath.addAll(path.getSourcepath());
					sourcepath = (File[]) newSourcepath.toArray(sourcepath);
				} catch (Exception e) {
					if (eclipse.length() > 0)
						error("Eclipse specified (" + eclipse
								+ ") but getting error processing: " + e);
				}
			} else {
				if (eclipse.length() > 0)
					error("Eclipse specified (" + eclipse
							+ ") but no project directory found");
			}
		}
		builder.setClasspath(classpath);
		builder.setSourcepath(sourcepath);
	}

	private void doHelp() {
		doHelp(new String[0], 0);
	}

	private void doHelp(String[] args, int i) {
		if (args.length <= i) {
			out
					.println("bnd -failok? -exceptions? ( wrap | print | build | eclipse | xref | view )?");
			out.println("See http://www.aQute.biz/Code/Bnd");
		} else {
			while (args.length > i) {
				if ("wrap".equals(args[i])) {
					out
							.println("bnd wrap (-output <pom|dir>)? (-properties <pom>)? <jar-pom>");
				} else if ("print".equals(args[i])) {
					out
							.println("bnd wrap -verify? -manifest? -list? -eclipse <jar-pom>");
				} else if ("build".equals(args[i])) {
					out
							.println("bnd build (-output <pom|dir>)? (-classpath <list>)? (-sourcepath <list>)? ");
					out
							.println("    -eclipse? -noeclipse? -sources? <bnd-pom>");
				} else if ("eclipse".equals(args[i])) {
					out.println("bnd eclipse");
				} else if ("view".equals(args[i])) {
					out.println("bnd view <pom.jar> <resource-names>+");
				}
				i++;
			}
		}
	}

	final static int VERIFY = 1;

	final static int MANIFEST = 2;

	final static int LIST = 4;

	final static int ECLIPSE = 8;
	final static int IMPEXP = 16;
	final static int USES = 32;
	final static int USEDBY = 64;
	final static int COMPONENT = 128;

	static final int HEX = 0;

	private void doPrint(String[] args, int i) throws Exception {
		int options = 0;

		for (; i < args.length; i++) {
			if ("-verify".startsWith(args[i]))
				options |= VERIFY;
			else if ("-manifest".startsWith(args[i]))
				options |= MANIFEST;
			else if ("-list".startsWith(args[i]))
				options |= LIST;
			else if ("-eclipse".startsWith(args[i]))
				options |= ECLIPSE;
			else if ("-impexp".startsWith(args[i]))
				options |= IMPEXP;
			else if ("-uses".startsWith(args[i]))
				options |= USES;
			else if ("-usedby".startsWith(args[i]))
				options |= USEDBY;
			else if ("-component".startsWith(args[i]))
				options |= COMPONENT;
			else if ("-all".startsWith(args[i]))
				options = -1;
			else {
				if (args[i].startsWith("-"))
					error("Invalid option for print: " + args[i]);
				else
					doPrint(args[i], options);
			}
		}
	}

	public void doPrint(String string, int options) throws Exception {
		File file = new File(string);
		if (!file.exists())
			error("File to print not found: " + string);
		else {
			if (options == 0)
				options = VERIFY | MANIFEST | IMPEXP | USES;
			doPrint(file, options);
		}
	}

	private void doPrint(File file, int options) throws ZipException,
			IOException, Exception {

		Jar jar = new Jar(file.getName(), file);
		try {
			if ((options & VERIFY) != 0) {
				Verifier verifier = new Verifier(jar);
				verifier.setPedantic(isPedantic());
				verifier.verify();
				getInfo(verifier);
			}
			if ((options & MANIFEST) != 0) {
				Manifest manifest = jar.getManifest();
				if (manifest == null)
					warning("JAR pom has no manifest " + file);
				else {
					out.println("[MANIFEST " + jar.getName() + "]");
					SortedSet<String> sorted = new TreeSet<String>();
					for (Object element : manifest.getMainAttributes().keySet()) {
						sorted.add(element.toString());
					}
					for (String key : sorted) {
						Object value = manifest.getMainAttributes().getValue(
								key);
						format("%-40s %-40s\r\n", new Object[] { key, value });
					}
				}
				out.println();
			}
			if ((options & IMPEXP) != 0) {
				out.println("[IMPEXP]");
				Manifest m = jar.getManifest();
				if (m != null) {
					Map<String, Map<String, String>> imports = parseHeader(m
							.getMainAttributes().getValue(
									Analyzer.IMPORT_PACKAGE));
					Map<String, Map<String, String>> exports = parseHeader(m
							.getMainAttributes().getValue(
									Analyzer.EXPORT_PACKAGE));
					imports.keySet().removeAll(exports.keySet());
					print("Import-Package",
							new TreeMap<String, Map<String, String>>(imports));
					print("Export-Package",
							new TreeMap<String, Map<String, String>>(exports));
				} else
					warning("File has no manifest");
			}

			if ((options & (USES | USEDBY)) != 0) {
				out.println();
				Analyzer analyzer = new Analyzer();
				analyzer.setPedantic(isPedantic());
				analyzer.setJar(jar);
				analyzer.analyze();
				if ((options & USES) != 0) {
					out.println("[USES]");
					printMapOfSets(new TreeMap<String, Set<String>>(analyzer
							.getUses()));
					out.println();
				}
				if ((options & USEDBY) != 0) {
					out.println("[USEDBY]");
					printMapOfSets(invertMapOfCollection(analyzer.getUses()));
				}
			}

			if ((options & COMPONENT) != 0) {
				printComponents(out, jar);
			}

			if ((options & LIST) != 0) {
				out.println("[LIST]");
				for (Map.Entry<String, Map<String, Resource>> entry : jar
						.getDirectories().entrySet()) {
					String name = entry.getKey();
					Map<String, Resource> contents = entry.getValue();
					out.println(name);
					if (contents != null) {
						for (String element : contents.keySet()) {
							int n = element.lastIndexOf('/');
							if (n > 0)
								element = element.substring(n + 1);
							out.print("  ");
							out.print(element);
							String path = element;
							if (name.length() != 0)
								path = name + "/" + element;
							Resource r = contents.get(path);
							if (r != null) {
								String extra = r.getExtra();
								if (extra != null) {

									out.print(" extra='" + escapeUnicode(extra)
											+ "'");
								}
							}
							out.println();
						}
					} else {
						out.println(name + " <no contents>");
					}
				}
				out.println();
			}
		} finally {
			jar.close();
		}
	}

	private final char nibble(int i) {
		return "0123456789ABCDEF".charAt(i & 0xF);
	}

	private final String escapeUnicode(String s) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c >= ' ' && c <= '~' && c != '\\')
				sb.append(c);
			else {
				sb.append("\\u");
				sb.append(nibble(c >> 12));
				sb.append(nibble(c >> 8));
				sb.append(nibble(c >> 4));
				sb.append(nibble(c));
			}
		}
		return sb.toString();
	}

	/**
	 * Print the components in this JAR pom.
	 * 
	 * @param jar
	 */
	private void printComponents(PrintStream out, Jar jar) throws Exception {
		out.println("[COMPONENTS]");
		Manifest manifest = jar.getManifest();
		if (manifest == null) {
			out.println("No manifest");
			return;
		}

		String componentHeader = manifest.getMainAttributes().getValue(
				Constants.SERVICE_COMPONENT);
		Map<String, Map<String, String>> clauses = parseHeader(componentHeader);
		for (String path : clauses.keySet()) {
			out.println(path);

			Resource r = jar.getResource(path);
			if (r != null) {
				InputStreamReader ir = new InputStreamReader(r
						.openInputStream());
				OutputStreamWriter or = new OutputStreamWriter(out);
				try {
					copy(ir, or);
				} finally {
					or.flush();
					ir.close();
				}
			} else {
				out.println("  - no resource");
				warning("No Resource found for service component: " + path);
			}
		}
		out.println();
	}

	Map<String, Set<String>> invertMapOfCollection(Map<String, Set<String>> map) {
		Map<String, Set<String>> result = new TreeMap<String, Set<String>>();
		for (Map.Entry<String, Set<String>> entry : map.entrySet()) {
			String name = entry.getKey();
			if (name.startsWith("java.") && !name.equals("java.sql"))
				continue;

			Collection<String> used = entry.getValue();
			for (String n : used) {
				if (n.startsWith("java.") && !n.equals("java.sql"))
					continue;
				Set<String> set = result.get(n);
				if (set == null) {
					set = new TreeSet<String>();
					result.put(n, set);
				}
				set.add(name);
			}
		}
		return result;
	}

	void printMapOfSets(Map<String, Set<String>> map) {
		for (Map.Entry<String, Set<String>> entry : map.entrySet()) {
			String name = entry.getKey();
			Set<String> used = new TreeSet<String>(entry.getValue());

			for (Iterator<String> k = used.iterator(); k.hasNext();) {
				String n = (String) k.next();
				if (n.startsWith("java.") && !n.equals("java.sql"))
					k.remove();
			}
			String list = vertical(40, used);
			format("%-40s %s", new Object[] { name, list });
		}
	}

	String vertical(int padding, Set<String> used) {
		StringBuffer sb = new StringBuffer();
		String del = "";
		for (Iterator<String> u = used.iterator(); u.hasNext();) {
			String name = (String) u.next();
			sb.append(del);
			sb.append(name);
			sb.append("\r\n");
			del = pad(padding);
		}
		if (sb.length() == 0)
			sb.append("\r\n");
		return sb.toString();
	}

	String pad(int i) {
		StringBuffer sb = new StringBuffer();
		while (i-- > 0)
			sb.append(' ');
		return sb.toString();
	}

	/**
	 * View files from JARs
	 * 
	 * We parse the commandline and print each pom on it.
	 * 
	 * @param args
	 * @param i
	 * @throws Exception
	 */
	private void doView(String[] args, int i) throws Exception {
		int options = 0;
		String charset = "UTF-8";
		File output = null;

		for (; i < args.length; i++) {
			if ("-charset".startsWith(args[i]))
				charset = args[++i];
			else if ("-output".startsWith(args[i])) {
				output = new File(args[++i]);
			} else
				break;
		}

		if (i >= args.length) {
			error("Insufficient arguments for view, no JAR pom");
			return;
		}
		String jar = args[i++];
		if (i >= args.length) {
			error("No Files to view");
			return;
		}

		doView(jar, args, i, charset, options, output);
	}

	private void doView(String jar, String[] args, int i, String charset,
			int options, File output) {
		File path = new File(jar).getAbsoluteFile();
		File dir = path.getParentFile();
		if (dir == null) {
			dir = new File("");
		}
		if (!dir.exists()) {
			error("No such pom: " + dir.getAbsolutePath());
			return;
		}

		String name = path.getName();
		if (name == null)
			name = "META-INF/MANIFEST.MF";

		Instruction instruction = Instruction.getPattern(path.getName());

		File[] children = dir.listFiles();
		for (int j = 0; j < children.length; j++) {
			String base = children[j].getName();
			// out.println("Considering: " +
			// children[j].getAbsolutePath() + " " +
			// instruction.getPattern());
			if (instruction.matches(base) ^ instruction.isNegated()) {
				for (; i < args.length; i++) {
					doView(children[j], args[i], charset, options, output);
				}
			}
		}
	}

	private void doView(File file, String resource, String charset,
			int options, File output) {
		// out.println("doView:" + pom.getAbsolutePath() );
		try {
			Instruction instruction = Instruction.getPattern(resource);
			FileInputStream fin = new FileInputStream(file);
			ZipInputStream in = new ZipInputStream(fin);
			ZipEntry entry = in.getNextEntry();
			while (entry != null) {
				// out.println("view " + pom + ": "
				// + instruction.getPattern() + ": " + entry.getName()
				// + ": " + output + ": "
				// + instruction.matches(entry.getName()));
				if (instruction.matches(entry.getName())
						^ instruction.isNegated())
					doView(entry.getName(), in, charset, options, output);
				in.closeEntry();
				entry = in.getNextEntry();
			}
			in.close();
			fin.close();
		} catch (Exception e) {
			out.println("Can not process: " + file.getAbsolutePath());
			e.printStackTrace();
		}
	}

	private void doView(String name, ZipInputStream in, String charset,
			int options, File output) throws Exception {
		int n = name.lastIndexOf('/');
		name = name.substring(n + 1);

		InputStreamReader rds = new InputStreamReader(in, charset);
		OutputStreamWriter wrt = new OutputStreamWriter(out);
		if (output != null)
			if (output.isDirectory())
				wrt = new FileWriter(new File(output, name));
			else
				wrt = new FileWriter(output);

		copy(rds, wrt);
		// rds.close(); also closes the stream which closes our zip pom it
		// seems
		if (output != null)
			wrt.close();
		else
			wrt.flush();
	}

	private void copy(Reader rds, Writer wrt) throws IOException {
		char buffer[] = new char[1024];
		int size = rds.read(buffer);
		while (size > 0) {
			wrt.write(buffer, 0, size);
			size = rds.read(buffer);
		}
	}

	private void print(String msg, Map<String, Map<String, String>> ports) {
		if (ports.isEmpty())
			return;
		out.println(msg);
		for (Map.Entry<String, Map<String, String>> entry : ports.entrySet()) {
			String key = entry.getKey();
			Map<String, String> clause = Create.copy(entry.getValue());
			clause.remove("uses:");
			format("  %-38s %s\r\n", key.trim(), clause.isEmpty() ? "" : clause
					.toString());
		}
	}

	private void format(String string, Object... objects) {
		if (objects == null || objects.length == 0)
			return;

		StringBuffer sb = new StringBuffer();
		int index = 0;
		for (int i = 0; i < string.length(); i++) {
			char c = string.charAt(i);
			switch (c) {
			case '%':
				String s = objects[index++] + "";
				int width = 0;
				int justify = -1;

				i++;

				c = string.charAt(i++);
				switch (c) {
				case '-':
					justify = -1;
					break;
				case '+':
					justify = 1;
					break;
				case '|':
					justify = 0;
					break;
				default:
					--i;
				}
				c = string.charAt(i++);
				while (c >= '0' && c <= '9') {
					width *= 10;
					width += c - '0';
					c = string.charAt(i++);
				}
				if (c != 's') {
					throw new IllegalArgumentException(
							"Invalid sprintf format:  " + string);
				}

				if (s.length() > width)
					sb.append(s);
				else {
					switch (justify) {
					case -1:
						sb.append(s);
						for (int j = 0; j < width - s.length(); j++)
							sb.append(" ");
						break;

					case 1:
						for (int j = 0; j < width - s.length(); j++)
							sb.append(" ");
						sb.append(s);
						break;

					case 0:
						int spaces = (width - s.length()) / 2;
						for (int j = 0; j < spaces; j++)
							sb.append(" ");
						sb.append(s);
						for (int j = 0; j < width - s.length() - spaces; j++)
							sb.append(" ");
						break;
					}
				}
				break;

			default:
				sb.append(c);
			}
		}
		out.print(sb);
	}

	private void doWrap(String[] args, int i) throws Exception {
		int options = 0;
		File properties = null;
		File output = null;
		File classpath[] = null;
		for (; i < args.length; i++) {
			if ("-output".startsWith(args[i]))
				output = new File(args[++i]);
			else if ("-properties".startsWith(args[i]))
				properties = new File(args[++i]);
			else if ("-classpath".startsWith(args[i])) {
				classpath = getClasspath(args[++i]);
			} else {
				File bundle = new File(args[i]);
				doWrap(properties, bundle, output, classpath, options, null);
			}
		}
	}

	public boolean doWrap(File properties, File bundle, File output,
			File classpath[], int options, Map<String, String> additional)
			throws Exception {
		if (!bundle.exists()) {
			error("No such pom: " + bundle.getAbsolutePath());
			return false;
		} else {
			Analyzer analyzer = new Analyzer();
			try {
				analyzer.setPedantic(isPedantic());
				analyzer.setJar(bundle);
				Jar dot = analyzer.getJar();

				if (properties != null) {
					analyzer.setProperties(properties);
				}
				if (additional != null)
					analyzer.putAll(additional, false);

				if (analyzer.getProperty(Analyzer.IMPORT_PACKAGE) == null)
					analyzer.setProperty(Analyzer.IMPORT_PACKAGE,
							"*;resolution:=optional");

				if (analyzer.getProperty(Analyzer.BUNDLE_SYMBOLICNAME) == null) {
					Pattern p = Pattern.compile("("
							+ Verifier.SYMBOLICNAME.pattern()
							+ ")(-[0-9])?.*\\.jar");
					String base = bundle.getName();
					Matcher m = p.matcher(base);
					base = "Untitled";
					if (m.matches()) {
						base = m.group(1);
					} else {
						error("Can not calculate name of output bundle, rename jar or use -properties");
					}
					analyzer.setProperty(Analyzer.BUNDLE_SYMBOLICNAME, base);
				}

				if (analyzer.getProperty(Analyzer.EXPORT_PACKAGE) == null) {
					String export = analyzer.calculateExportsFromContents(dot);
					analyzer.setProperty(Analyzer.EXPORT_PACKAGE, export);
				}

				if (classpath != null)
					analyzer.setClasspath(classpath);

				analyzer.mergeManifest(dot.getManifest());

				//
				// Cleanup the version ..
				//
				String version = analyzer.getProperty(Analyzer.BUNDLE_VERSION);
				if (version != null) {
					version = Builder.cleanupVersion(version);
					analyzer.setProperty(Analyzer.BUNDLE_VERSION, version);
				}

				if (output == null)
					if (properties != null)
						output = properties.getAbsoluteFile().getParentFile();
					else
						output = bundle.getAbsoluteFile().getParentFile();

				String path = bundle.getName();
				if (path.endsWith(DEFAULT_JAR_EXTENSION))
					path = path.substring(0, path.length()
							- DEFAULT_JAR_EXTENSION.length())
							+ DEFAULT_BAR_EXTENSION;
				else
					path = bundle.getName() + DEFAULT_BAR_EXTENSION;

				if (output.isDirectory())
					output = new File(output, path);

				analyzer.calcManifest();
				Jar jar = analyzer.getJar();
				getInfo(analyzer);
				statistics(jar, output, "");
				File f = File.createTempFile("tmpbnd", ".jar");
				f.deleteOnExit();
				try {
					jar.write(f);
					jar.close();
					if (!f.renameTo(output)) {
						copy(f, output);
					}
				} finally {
					f.delete();
				}
				return getErrors().size() == 0;
			} finally {
				analyzer.close();
			}
		}
	}

	void doDiff(String args[], int first) throws IOException {
		File base = new File("");
		boolean strict = false;
		Jar targets[] = new Jar[2];
		int n = 0;

		for (int i = first; i < args.length; i++) {
			if ("-d".equals(args[i]))
				base = getFile(base, args[++i]);
			else if ("-strict".equals(args[i]))
				strict = "true".equalsIgnoreCase(args[++i]);
			else if (args[i].startsWith("-"))
				error("Unknown option for diff: " + args[i]);
			else {
				if (n >= 2)
					System.err.println("Must have 2 files ... not more");
				else {
					File f = getFile(base, args[i]);
					if (!f.isFile())
						System.err.println("Not a pom: " + f);
					else {
						try {
							Jar jar = new Jar(f);
							targets[n++] = jar;
						} catch (Exception e) {
							System.err.println("Not a JAR pom: " + f);
						}
					}
				}
			}
		}
		if (n != 2) {
			System.err.println("Must have 2 files ...");
			return;
		}
		Diff diff = new Diff();

		Map<String, Object> map = diff.diff(targets[0], targets[1], strict);
		diff.print(System.out, map, 0);

		for (Jar jar : targets) {
			jar.close();
		}
		diff.close();
	}

	void copy(File a, File b) {
		try {
			InputStream in = new FileInputStream(a);
			OutputStream out = new FileOutputStream(b);
			byte[] buffer = new byte[8196];
			int size = in.read(buffer);
			while (size > 0) {
				out.write(buffer, 0, size);
				size = in.read(buffer);
			}
			in.close();
			out.close();
		} catch (IOException e) {
			error("While copying the output pom: %s -> %s", e, a, b);
		}
	}

	public void setOut(PrintStream out) {
		this.out = out;
	}

	/**
	 * Run a test
	 * 
	 * @throws Exception
	 */

	public void test(String args[], int i) throws Exception {
		Project project = getProject();
		project.test();
		getInfo(project);
	}

	public Project getProject() throws Exception {
		if (project != null)
			return project;

		project = Workspace.getProject(getBase());
		if (!project.isValid())
			throw new IllegalArgumentException("The base directory "
					+ getBase() + " is not a project directory ");

		return project;
	}

	/**
	 * Printout all the variables.
	 * 
	 * @param args
	 * @param i
	 * @throws Exception
	 */
	public void debug(String args[], int i) throws Exception {
		Project project = getProject();
		System.out.println("Project: " + project);
		Properties p = project.getFlattenedProperties();
		for (Object k : p.keySet()) {
			String key = (String) k;
			String s = p.getProperty(key);
			Collection<String> l = null;

			if (s.indexOf(',') > 0)
				l = split(s);
			else if (s.indexOf(':') > 0)
				l = split(s, "\\s*:\\s*");
			if (l != null) {
				String del = key;
				for (String ss : l) {
					System.out.printf("%-40s %s\n", del, ss);
					del = "";
				}
			} else
				System.out.printf("%-40s %s\n", key, s);
		}
	}

	/**
	 * Manage the repo.
	 * 
	 * <pre>
	 *  repo
	 *      list
	 *      put &lt;pom|url&gt;
	 *      get &lt;bsn&gt; (&lt;version&gt;)?
	 *      fetch &lt;pom|url&gt;
	 * </pre>
	 */

	public void repo(String args[], int i) throws Exception {
		String bsn = null;
		String version = null;

		Project p = Workspace.getProject(getBase());
		List<RepositoryPlugin> repos = p.getPlugins(RepositoryPlugin.class);
		RepositoryPlugin writable = null;
		for (Iterator<RepositoryPlugin> rp = repos.iterator(); rp.hasNext();) {
			RepositoryPlugin rpp = rp.next();
			if (rpp.canWrite()) {
				writable = rpp;
				break;
			}
		}

		for (; i < args.length; i++) {
			if ("repos".equals(args[i])) {
				int n = 0;
				for (RepositoryPlugin repo : repos) {
					out.printf("%3d. %s\n", n++, repo);
				}
			} else if ("list".equals(args[i])) {
				String mask = null;
				if (i < args.length - 1) {
					mask = args[++i];
				}
				repoList(repos, mask);
			} else if ("-repo".equals(args[i])) {
				String location = args[++i];
				if (location.equals("maven")) {
					System.out.println("Maven");
					MavenRepository maven = new MavenRepository();
					maven.setProperties(new HashMap<String, String>());
					maven.setReporter(this);
					repos = Arrays.asList((RepositoryPlugin) maven);
				} else {
					FileRepo repo = new FileRepo();
					repo.setReporter(this);
					repo.setLocation(location);
					repos = Arrays.asList((RepositoryPlugin) repo);
					writable = repo;
				}
			} else if ("-bsn".equals(args[i])) {
				bsn = args[++i];
			} else if ("-version".equals(args[i])) {
				version = args[++i];
			} else if ("spring".equals(args[i])) {
				if (bsn == null || version == null) {
					error("-bsn and -version must be set before spring command is used");
				} else {
					String url = String
							.format(
									"http://www.springsource.com/repository/app/bundle/version/download?name=%s&version=%s&type=binary",
									bsn, version);
					repoPut(writable, p, url, bsn, version);
				}
			} else if ("put".equals(args[i]))
				while (++i < args.length) {
					repoPut(writable, p, args[i], bsn, version);
				}
			else if ("get".equals(args[i]))
				repoGet(repos, args[++i]);
			else
				repoFetch(repos, args[++i]);
		}
	}

	private void repoGet(List<RepositoryPlugin> writable, String string) {

	}

	private void repoPut(RepositoryPlugin writable, Project project,
			String file, String bsn, String version) throws Exception {
		Jar jar = null;
		int n = file.indexOf(':');
		if (n > 1 && n < 10) {
			jar = project.getValidJar(new URL(file));
		} else {
			File f = getFile(file);
			if (f.isFile()) {
				jar = project.getValidJar(f);
			}
		}

		if (jar != null) {
			Manifest manifest = jar.getManifest();
			if (bsn != null)
				manifest.getMainAttributes().putValue(
						Constants.BUNDLE_SYMBOLICNAME, bsn);
			if (version != null)
				manifest.getMainAttributes().putValue(Constants.BUNDLE_VERSION,
						version);

			writable.put(jar);

		} else
			error("There is no such pom or url: " + file);
	}

	private void repoFetch(List<RepositoryPlugin> repos, String string) {
		File f = getFile(string);
		if (f.isFile()) {
		} else {
			// try {
			// URL url = new URL(string);
			// } catch (MalformedURLException mue) {
			//
			// }
		}

	}

	void repoList(List<RepositoryPlugin> repos, String mask) throws Exception {
		trace("list repo " + repos + " " + mask);
		Set<String> bsns = new TreeSet<String>();
		for (RepositoryPlugin repo : repos) {
			bsns.addAll(repo.list(mask));
		}

		for (String bsn : bsns) {
			Set<Version> versions = new TreeSet<Version>();
			for (RepositoryPlugin repo : repos) {
				List<Version> result = repo.versions(bsn);
				if (result != null)
					versions.addAll(result);
			}
			out.printf("%-40s %s\n", bsn, versions);
		}
	}

	/**
	 * Patch
	 */

	void patch(String args[], int i) throws Exception {
		for (; i < args.length; i++) {
			if ("create".equals(args[i]) && i + 3 < args.length) {
				createPatch(args[++i], args[++i], args[++i]);
			} else if ("apply".equals(args[i]) && i + 3 < args.length) {
				applyPatch(args[++i], args[++i], args[++i]);
			} else if ("help".equals(args[i])) {
				out
						.println("patch (create <old> <new> <patch> | patch <old> <patch> <new>)");
			} else
				out.println("Patch does not recognize command? "
						+ Arrays.toString(args));
		}
	}

	void createPatch(String old, String newer, String patch) throws Exception {
		Jar a = new Jar(new File(old));
		Manifest am = a.getManifest();
		Jar b = new Jar(new File(newer));
		Manifest bm = b.getManifest();

		Set<String> delete = newSet();

		for (String path : a.getResources().keySet()) {
			Resource br = b.getResource(path);
			if (br == null) {
				trace("DELETE    %s", path);
				delete.add(path);
			} else {
				Resource ar = a.getResource(path);
				if (isEqual(ar, br)) {
					trace("UNCHANGED %s", path);
					b.remove(path);
				} else
					trace("UPDATE    %s", path);
			}
		}

		bm.getMainAttributes().putValue("Patch-Delete", join(delete, ", "));
		bm.getMainAttributes().putValue("Patch-Version",
				am.getMainAttributes().getValue("Bundle-Version"));

		b.write(new File(patch));
		a.close();
		a.close();
	}

	private boolean isEqual(Resource ar, Resource br) throws Exception {
		InputStream ain = ar.openInputStream();
		try {
			InputStream bin = br.openInputStream();
			try {
				while (true) {
					int an = ain.read();
					int bn = bin.read();
					if (an == bn) {
						if (an == -1)
							return true;
					} else
						return false;
				}
			} finally {
				bin.close();
			}
		} finally {
			ain.close();
		}
	}

	void applyPatch(String old, String patch, String newer) throws Exception {
		Jar a = new Jar(new File(old));
		Jar b = new Jar(new File(patch));
		Manifest bm = b.getManifest();

		String patchDelete = bm.getMainAttributes().getValue("Patch-Delete");
		String patchVersion = bm.getMainAttributes().getValue("Patch-Version");
		if (patchVersion == null) {
			error("To patch, you must provide a patch bundle.\nThe given "
					+ patch
					+ " bundle does not contain the Patch-Version header");
			return;
		}

		Collection<String> delete = split(patchDelete);
		Set<String> paths = new HashSet<String>(a.getResources().keySet());
		paths.removeAll(delete);

		for (String path : paths) {
			Resource br = b.getResource(path);
			if (br == null)
				b.putResource(path, a.getResource(path));
		}

		bm.getMainAttributes().putValue("Bundle-Version", patchVersion);
		b.write(new File(newer));
		a.close();
		b.close();
	}

	/**
	 * Run the tests from a prepared bnd pom.
	 * 
	 * @param args
	 * @param i
	 * @throws Exception
	 */
	public void runtests(String args[], int i) throws Exception {
		int errors = 0;
		File cwd = new File("").getAbsoluteFile();
		Workspace ws = Workspace.getWorkspace(cwd);
		File reportDir = getFile("reports");

		Tag summary = new Tag("summary");
		summary.addAttribute("date", new Date());
		summary.addAttribute("ws", ws.getBase());

		boolean hadOne = false;
		for (; i < args.length; i++) {
			if (args[i].startsWith("-reportdir")) {
				reportDir = getFile(args[i]).getAbsoluteFile();
				if (reportDir.isFile())
					error("-reportdir must be a directory");
			} else if (args[i].startsWith("-title")) {
				summary.addAttribute("title", args[++i]);
			} else if (args[i].startsWith("-dir")) {
				cwd = getFile(args[++i]).getAbsoluteFile();
			} else {
				File f = getFile(args[i]);
				errors += runtTest(f, ws, reportDir, summary);
				hadOne = true;
			}
		}

		if (!hadOne) {
			// See if we had any, if so, just use all files in
			// the current directory
			File[] files = cwd.listFiles();
			for (File f : files) {
				if (f.getName().endsWith(".bnd"))
					errors += runtTest(f, ws, reportDir, summary);
			}
		}

		if (errors > 0)
			summary.addAttribute("errors", errors);

		File r = getFile(reportDir + "/summary.xml");
		FileOutputStream out = new FileOutputStream(r);
		PrintWriter pw = new PrintWriter(new OutputStreamWriter(out));
		try {
			summary.print(0, pw);
		} finally {
			pw.close();
			out.close();
		}
	}

	private int runtTest(File testFile, Workspace ws, File reportDir,
			Tag summary) throws Exception {
		System.out.println("Report dir=" + reportDir + " testFile=" + testFile);
		reportDir.mkdirs();
		int errors = -1;
		Tag report = new Tag("report");
		summary.addContent(report);

		report.addAttribute("path", testFile.getAbsolutePath());
		if (!testFile.isFile()) {
			error("No bnd pom: " + testFile);
			report.addAttribute("exception", "No bnd pom found");
		} else {
			long start = System.currentTimeMillis();
			errors = runtTests(report, ws, reportDir, testFile);

			long duration = System.currentTimeMillis() - start;
			report.addAttribute("duration", (duration + 500) / 1000);
		}
		return errors;
	}

	private int runtTests(Tag report, Workspace ws, File reportDir, File f)
			throws Exception {
		int errors = -1;
		Project p = new Project(ws, f.getAbsoluteFile().getParentFile(), f
				.getAbsoluteFile());
		ProjectLauncher pl = new ProjectLauncher(p);
		String t = p.getProperty("-target");
		if (t == null) {
			error("No target set for " + f);
			report.addAttribute("exception", "No -target property found");
		} else {
			List<Container> targets = p.getBundles(Constants.STRATEGY_HIGHEST,
					t);
			if (targets.size() != 1) {
				error("Only one -target supported " + t);
				report.addAttribute("exception", "Only one -target supported "
						+ t);
			} else {

				for (Container c : targets) {
					File target = c.getFile();

					if (!target.isFile())
						error("The target is not a proper JAR pom: " + target);
					else {
						report.addAttribute("title", f.getName().replace(
								".bnd", ""));
						report.addAttribute("id", target.getName());
						errors = runtActualTests(report, reportDir, f, pl,
								target);
					}
				}
			}
		}
		return errors;
	}

	/**
	 * Run the actual test.
	 * 
	 * @param report
	 * @param reportDir
	 * @param f
	 * @param pl
	 * @param target
	 * @return
	 * @throws Exception
	 */
	private int runtActualTests(Tag report, File reportDir, File f,
			ProjectLauncher pl, File target) throws Exception {
		int errors;
		String path = f.getName().replace(".bnd", "") + ".xml";
		File reportFile = getFile(reportDir, path);
		pl.setReport(reportFile);
		report.addAttribute("report", path);

		errors = pl.run(target);

		getInfo(pl);
		if (errors == 0) {
			trace("ok");
		} else {
			report.addAttribute("error", errors);
			error("Failed: " + normalize(f) + ", " + errors + " test"
					+ (errors > 1 ? "s" : "") + " failures, see "
					+ normalize(pl.getTestreport().getAbsolutePath()));
		}

		doPerReport(report, reportFile);
		return errors;
	}

	/**
	 * Calculate the coverage if there is coverage info in the test pom.
	 */

	private void doPerReport(Tag report, File file) throws Exception {
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory
					.newInstance();
			factory.setNamespaceAware(true); // never forget this!
			DocumentBuilder builder = factory.newDocumentBuilder();
			Document doc = builder.parse(file);

			XPathFactory xpathFactory = XPathFactory.newInstance();
			XPath xpath = xpathFactory.newXPath();

			doCoverage(report, doc, xpath);
			doHtmlReport(report, file, doc, xpath);

		} catch (Exception e) {
			report.addAttribute("coverage-failed", e.getMessage());
		}
	}

	private void doCoverage(Tag report, Document doc, XPath xpath)
			throws XPathExpressionException {
		int bad = Integer.parseInt(xpath.evaluate(
				"count(//method[count(ref)<2])", doc));
		int all = Integer.parseInt(xpath.evaluate("count(//method)", doc));
		report.addAttribute("coverage-bad", bad);
		report.addAttribute("coverage-all", all);
	}

	private void doHtmlReport(Tag report, File file, Document doc, XPath xpath)
			throws Exception {
		String path = file.getAbsolutePath();
		if (path.endsWith(".xml"))
			path = path.substring(0, path.length() - 4);
		path += ".html";
		File html = new File(path);
		System.out.println("Creating html report: " + html);

		TransformerFactory fact = TransformerFactory.newInstance();

		InputStream in = getClass().getResourceAsStream("testreport.xsl");
		if (in == null) {
			warning("Resource not found: test-report.xsl, no html report");
		} else {
			FileWriter out = new FileWriter(html);
			try {
				Transformer transformer = fact.newTransformer(new StreamSource(
						in));
				transformer
						.transform(new DOMSource(doc), new StreamResult(out));
				System.out.println("Transformed");
			} finally {
				in.close();
				out.close();
			}
		}
	}

	/**
	 * Extract a pom from the JAR
	 */

	public void doExtract(String args[], int i) throws Exception {
		if (i >= args.length) {
			error("No arguments for extract");
			return;
		}

		File f = getFile(args[i++]);
		if (!f.isFile()) {
			error("No JAR pom to extract from: %s", f);
			return;
		}

		if (i == args.length) {
			System.out.println("FILES:");
			doPrint(f, LIST);
			return;
		}
		Jar j = new Jar(f);
		try {
			Writer output = new OutputStreamWriter(out);
			while (i < args.length) {
				String path = args[i++];

				Resource r = j.getResource(path);
				if (r == null)
					error("No such resource: %s in %s", path, f);
				else {
					InputStream in = r.openInputStream();
					try {
						InputStreamReader rds = new InputStreamReader(in);
						copy(rds, output);
						output.flush();
					} finally {
						in.close();
					}
				}
			}
		} finally {
			j.close();
		}

	}

	void doDot(String args[], int i) throws Exception {
		File out = getFile("graph.gv");
		Builder b = new Builder();

		for (; i < args.length; i++) {
			if ("-o".equals(args[i]))
				out = getFile(args[++i]);
			else if (args[i].startsWith("-"))
				error("Unknown option for dot: %s", args[i]);
			else
				b.addClasspath(getFile(args[i]));
		}
		b.setProperty(EXPORT_PACKAGE, "*");
		b.setPedantic(isPedantic());
		b.build();
		FileWriter os = new FileWriter(out);
		PrintWriter pw = new PrintWriter(os);
		try {
			pw.println("digraph bnd {");
			pw.println("  size=\"6,6\";");
			pw.println("node [color=lightblue2, style=filled,shape=box];");
			for (Map.Entry<String, Set<String>> uses : b.getUses().entrySet()) {
				for (String p : uses.getValue()) {
					if (!p.startsWith("java."))
						pw.printf("\"%s\" -> \"%s\";\n", uses.getKey(), p);
				}
			}
			pw.println("}");

		} finally {
			pw.close();
		}

	}

}
