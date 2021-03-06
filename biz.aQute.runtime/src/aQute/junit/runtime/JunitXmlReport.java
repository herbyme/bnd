package aQute.junit.runtime;

import java.io.*;
import java.net.*;
import java.text.*;
import java.util.*;

import junit.framework.*;

import org.osgi.framework.*;

public class JunitXmlReport implements TestReporter {
	Tag testsuite = new Tag("testsuite");
	Tag testcase;
	static String hostname;
	static DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
	long startTime;
	long testStartTime;
	int tests = 0;
	PrintWriter out;
	private Tee systemErr;
	private Tee systemOut;
	boolean finished;
	boolean progress;
	
	public class LogEntry {
		String clazz;
		String name;
		String message;
	}

	public JunitXmlReport(String reportName) throws Exception {
		if (hostname == null)
			hostname = InetAddress.getLocalHost().getHostName();
		File file = new File(reportName);
		out = new PrintWriter(new FileWriter(file));
	}

	public void setProgress(boolean progress){
		this.progress= progress;
	}
	
	public void begin(Bundle fw, Bundle targetBundle, List classNames,
			int realcount) {
		startTime = System.currentTimeMillis();

		testsuite.addAttribute("hostname", hostname);
		testsuite
				.addAttribute("name", "test." + targetBundle.getSymbolicName());
		testsuite.addAttribute("timestamp", df.format(new Date()));
		testsuite.addAttribute("framework", fw);
		testsuite.addAttribute("framework-version", fw.getVersion());
        testsuite.addAttribute("target", targetBundle.getLocation());

		Tag properties = new Tag("properties");
		testsuite.addContent(properties);
		
		for (Iterator i = System.getProperties().entrySet().iterator(); i
				.hasNext();) {
			Map.Entry entry = (Map.Entry) i.next();
			Tag property = new Tag("property");
			property.addAttribute("name", entry.getKey());
			property.addAttribute("value", entry.getValue());
			properties.addContent(property);
		}

		Tag bundles = new Tag("bundles");
		testsuite.addContent(bundles);
		Bundle bs[] = fw.getBundleContext().getBundles();

		for (int i = 0; i < bs.length; i++) {
			Tag bundle = new Tag("bundle");
			bundle.addAttribute("location", bs[i].getLocation());
			bundle.addAttribute("modified", df.format(new Date(bs[i]
					.getLastModified())));
			bundle.addAttribute("state", bs[i].getState());
			bundle.addAttribute("id", bs[i].getBundleId() + "");
			bundle.addAttribute("bsn", bs[i].getSymbolicName());
			bundle.addAttribute("version", bs[i].getVersion());

			if (bs[i].equals(targetBundle))
				bundle.addAttribute("target", "true");

			bundles.addContent(bundle);
		}
	}

	public void end() {
		if (!finished) {
			finished = true;
			testsuite.addAttribute("tests", tests);
			testsuite.addAttribute("time", getFraction(System
					.currentTimeMillis()
					- startTime, 1000));
			testsuite.addAttribute("timestamp", df.format(new Date()));
			testsuite.print(0, out);
			out.close();
		}
	}

	private String getFraction(long l, int i) {
		return (l / 1000) + "." + (l % 1000);
	}

	// <testcase classname="test.AnnotationsTest" name="testComponentReader"
	// time="0.045" />
	public void startTest(Test test) {
		testcase = new Tag("testcase");
		testsuite.addContent(testcase);
		testcase.addAttribute("classname", test.getClass().getName());
		String nameAndClass = test.toString();
		String name = nameAndClass;

		int n = nameAndClass.indexOf('(');
		if (n > 0 && nameAndClass.endsWith(")")) {
			name = nameAndClass.substring(0, n);
		}

		testcase.addAttribute("name", name);
		
		capture();
		testStartTime = System.currentTimeMillis();
		progress(name);
	}

	public void setTests(List flattened) {
	}

	// <testcase classname="test.AnalyzerTest" name="testMultilevelInheritance"
	// time="0.772">
	// <error type="java.lang.Exception">java.lang.Exception:
	// at test.AnalyzerTest.testMultilevelInheritance(AnalyzerTest.java:47)
	// </error>
	// </testcase>

	public void addError(Test test, Throwable t) {
		Tag error = new Tag("error");
		error.setCDATA();
		error.addAttribute("type", t.getClass().getName());
		error.addContent(getTrace(t));
		testcase.addContent(error);
		progress(" e");
	}

	private void progress(String s) {
		if ( progress ) {
			systemOut.oldStream.print(s);
			systemOut.oldStream.flush();
		}
	}

	private String getTrace(Throwable t) {
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		pw.println(t.toString());
		
		StackTraceElement ste[] = t.getStackTrace();
		for ( int i=0; i<ste.length; i++) {
			pw.println("at " + ste[i].toString().trim());
		}
		pw.close();
		return sw.toString();
	}

	// <testcase classname="test.AnalyzerTest" name="testFindClass"
	// time="0.0050">
	// <failure
	// type="junit.framework.AssertionFailedError">junit.framework.AssertionFailedError
	// at test.AnalyzerTest.testFindClass(AnalyzerTest.java:25)
	// </failure>
	// <testcase>
	//
	public void addFailure(Test test, AssertionFailedError t) {
		Tag failure = new Tag("failure");
		failure.setCDATA();
		failure.addAttribute("type", t.getClass().getName());
		failure.addContent(getTrace(t));
		testcase.addContent(failure);
		progress(" f");
	}

	public void endTest(Test test) {
		testcase.addAttribute("time", getFraction(System.currentTimeMillis()-testStartTime, 1000));
		uncapture();
		if ( progress )
			System.out.println();
	}
	
	void capture() {
		if ( systemOut == null ) {
			systemOut = new Tee(System.out);
			System.setOut(systemOut.getStream());
			systemErr = new Tee(System.err);
			System.setErr(systemOut.getStream());
		}
	}

	void uncapture() {
		if (systemOut != null) {
			System.out.flush();
			System.err.flush();
			if ( systemOut.buffer.size() > 0)
				testcase.addContent( systemOut.getContent("system-out"));
			System.setOut( systemOut.oldStream);
			systemOut = null;
			if ( systemErr.buffer.size() > 0)
				testcase.addContent( systemErr.getContent("system-err"));
			System.setErr( systemErr.oldStream);
			systemErr = null;
		}
	}

	public void close() {
		uncapture();
		end();
	}

	public void aborted() {
		testsuite.addAttribute("aborted", "true");
		close();
	}
	
	public void addTag( Tag tag ) {
		testsuite.addContent( tag );
	}

}
