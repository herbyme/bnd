package test;

import java.io.*;
import java.util.*;

import junit.framework.*;
import aQute.lib.osgi.*;

public class IncludeHeaderTest extends TestCase {
    
    public void testTopBottom() throws Exception {
        Analyzer analyzer = new Analyzer();
        analyzer.setProperties(new File("src/test/include.bnd/top.bnd"));
        assertEquals("0.0.257", analyzer.getProperty("Bundle-Version"));
    }

	public void testPrecedence() throws Exception {
		File base  = new File("src/test");
		String a = "a=a.props\n";
		String b = "a=b.props\n";
		File aa = new File(base,"a.props");
		File bb = new File(base,"b.props");
		write(aa, a);
		write(bb, b );
		
		
		Analyzer analyzer = new Analyzer();
		analyzer.setBase(base);
		Properties x = new Properties();
		x.put("a", "x");		
		x.put("-include", "a.props, b.props");
		analyzer.setProperties(x);		
		assertEquals("b.props", analyzer.getProperty("a")); 	// from org
		
		analyzer = new Analyzer();
        analyzer.setBase(base);
		x = new Properties();
		x.put("a", "x");		
		x.put("-include", "~a.props, b.props");
		analyzer.setProperties(x);		
		assertEquals("b.props", analyzer.getProperty("a")); 	// from org

        analyzer = new Analyzer();
        analyzer.setBase(base);
		x = new Properties();
		x.put("a", "x");		
		x.put("-include", "a.props, ~b.props");
		analyzer.setProperties(x);		
		assertEquals("a.props", analyzer.getProperty("a")); 	// from org

        analyzer = new Analyzer();
        analyzer.setBase(base);
		x = new Properties();
		x.put("a", "x");		
		x.put("-include", "~a.props, ~b.props");
		analyzer.setProperties(x);		
		assertEquals("x", analyzer.getProperty("a")); 	// from org

		aa.delete();
		bb.delete();
	}
	
	private void write(File file, String b) throws Exception {
		FileOutputStream out = new FileOutputStream( file);
		out.write( b.getBytes());
		out.close();		
	}

	public void testAbsentIncludes() throws IOException {
		Analyzer analyzer = new Analyzer();
		analyzer.setBase(new File("src/test"));
		Properties p = new Properties();
		p.put("-include", "-iamnotthere.txt");
		analyzer.setProperties(p);
		System.out.println(analyzer.getErrors());
		assertEquals(0, analyzer.getErrors().size());
	}

	public void testIncludeWithProperty() throws IOException {
		File home = new File(System.getProperty("user.home"));
		File include = new File(home, "includeheadertest.txt");
		try {
			FileOutputStream fw = new FileOutputStream(include);
			fw.write("IncludeHeaderTest: yes\n\r".getBytes());
			fw.write("a: 2\n\r".getBytes());
			fw.write("b: ${a}\n\r".getBytes());
			fw.close();
			Analyzer analyzer = new Analyzer();
			analyzer.setBase(new File("src/test"));
			Properties p = new Properties();
			p.put("a", "1");
			p
					.put("-include",
							"-iamnotthere.txt, ${user.home}/includeheadertest.txt");
			analyzer.setProperties(p);
			String value = analyzer.getProperty("IncludeHeaderTest");
			assertEquals("yes", value);
			assertEquals("2", analyzer.getProperty("a"));
			assertEquals("2", analyzer.getProperty("b"));
			assertEquals(0, analyzer.getErrors().size());
		} finally {
			include.delete();
		}
	}

	public void testIncludeHeader() throws IOException {
		Analyzer analyzer = new Analyzer();
		analyzer.setBase(new File("src/test"));
		Properties p = new Properties();
		p.put("a", "1");
		p.put("-include", "includeheadertest.mf, includeheadertest.prop");
		analyzer.setProperties(p);
		System.out.println(analyzer.getProperties());
		assertEquals("1", analyzer.getProperty("a"));
		assertEquals("end", analyzer.getProperty("last-props"));
		assertEquals("end", analyzer.getProperty("last-manifest"));
		assertEquals("abcd", analyzer.getProperty("manifest"));
		assertEquals("abcd", analyzer.getProperty("props"));
		assertEquals("1", analyzer.getProperty("test"));
	}

}
