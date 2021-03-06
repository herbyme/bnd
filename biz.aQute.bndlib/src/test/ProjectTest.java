package test;

import java.io.File;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.Manifest;

import junit.framework.TestCase;
import aQute.bnd.build.Project;
import aQute.bnd.build.Workspace;
import aQute.lib.deployer.FileRepo;
import aQute.lib.osgi.Builder;
import aQute.lib.osgi.Jar;
import aQute.lib.osgi.Processor;
import aQute.lib.osgi.eclipse.EclipseClasspath;
import aQute.libg.version.Version;

public class ProjectTest extends TestCase {

	/**
	 * Check if the getSubBuilders properly predicts the output.
	 */

	public void testSubBuilders() throws Exception {
		Workspace ws = Workspace.getWorkspace(new File("test/ws"));
		Project project = ws.getProject("p4-sub");

		Collection<? extends Builder> bs = project.getSubBuilders();
		assertNotNull(bs);
		assertEquals(3, bs.size());
		Set<String> names = new HashSet<String>();
		for (Builder b : bs) {
			names.add(b.getBsn());
		}
		assertTrue(names.contains("p4-sub.a"));
		assertTrue(names.contains("p4-sub.b"));
		assertTrue(names.contains("p4-sub.c"));
		
		File[] files = project.build();
		System.out.println(Processor.join(project.getErrors(), "\n"));
		System.out.println(Processor.join(project.getWarnings(), "\n"));
		assertEquals(0, project.getErrors().size());
		assertEquals(0, project.getWarnings().size());
		assertNotNull(files);
		assertEquals(3, files.length);
		for ( File file : files ) {
			Jar jar = new Jar(file);
			Manifest m = jar.getManifest();
			assertTrue( names.contains(m.getMainAttributes().getValue("Bundle-SymbolicName")));
		}
	}

	/**
	 * Tests the handling of the -sub facility
	 * 
	 * @throws Exception
	 */

	public void testSub() throws Exception {
		Workspace ws = Workspace.getWorkspace(new File("test/ws"));
		Project project = ws.getProject("p4-sub");
		File[] files = project.build();
		System.out.println(Processor.join(project.getErrors(), "\n"));
		System.out.println(Processor.join(project.getWarnings(), "\n"));

		assertEquals(0, project.getErrors().size());
		assertEquals(0, project.getWarnings().size());
		assertNotNull(files);
		assertEquals(3, files.length);

		Jar a = new Jar(files[0]);
		Jar b = new Jar(files[1]);
		Manifest ma = a.getManifest();
		Manifest mb = b.getManifest();

		assertEquals("base", ma.getMainAttributes().getValue("Base-Header"));
		assertEquals("base", mb.getMainAttributes().getValue("Base-Header"));
		assertEquals("a", ma.getMainAttributes().getValue("Sub-Header"));
		assertEquals("b", mb.getMainAttributes().getValue("Sub-Header"));
	}

	public void testOutofDate() throws Exception {
		Workspace ws = Workspace.getWorkspace(new File("test/ws"));
		Project project = ws.getProject("p3");
		File bnd = new File("test/ws/p3/bnd.bnd");
		assertTrue(bnd.exists());

		project.clean();
		project.getTarget().mkdirs();

		// Now we build it.
		File[] files = project.build();
		assertNotNull(files);
		assertEquals(1, files.length);

		// Now we should not rebuild it
		long lastTime = files[0].lastModified();
		files = project.build();
		assertEquals(1, files.length);
		assertTrue(files[0].lastModified() == lastTime);

		Thread.sleep(2000);

		project.updateModified(System.currentTimeMillis(), "Testing");
		files = project.build();
		assertEquals(1, files.length);
		assertTrue("Must have newer files now",
				files[0].lastModified() > lastTime);
	}

	public void testRepoMacro() throws Exception {
		Workspace ws = Workspace.getWorkspace(new File("test/ws"));
		Project project = ws.getProject("p2");
		System.out.println(project.getPlugins(FileRepo.class));
		String s = project.getReplacer().process(("${repo;libtest}"));
		System.out.println(s);
		assertTrue(s
				.contains("org.apache.felix.configadmin/org.apache.felix.configadmin-1.1.0"));
		assertTrue(s
				.contains("org.apache.felix.ipojo/org.apache.felix.ipojo-1.0.0.jar"));
	}

	public void testClasspath() throws Exception {
		File project = new File("").getAbsoluteFile();
		File workspace = project.getParentFile();
		Processor processor = new Processor();
		EclipseClasspath p = new EclipseClasspath(processor, workspace, project);
		System.out.println(p.getDependents());
		System.out.println(p.getClasspath());
		System.out.println(p.getSourcepath());
		System.out.println(p.getOutput());
	}

	public void testBump() throws Exception {
		Workspace ws = Workspace.getWorkspace(new File("test/ws"));
		Project project = ws.getProject("p1");
		int size = project.getProperties().size();
		Version old = new Version(project.getProperty("Bundle-Version"));
		project.bump("=+0");
		Version newv = new Version(project.getProperty("Bundle-Version"));
		assertEquals(old.getMajor(), newv.getMajor());
		assertEquals(old.getMinor() + 1, newv.getMinor());
		assertEquals(0, newv.getMicro());
		assertEquals(size, project.getProperties().size());
		assertEquals("sometime", newv.getQualifier());
	}

}
