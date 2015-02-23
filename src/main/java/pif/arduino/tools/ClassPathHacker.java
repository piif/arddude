package pif.arduino.tools;

// http://stackoverflow.com/questions/60764/how-should-i-load-jars-dynamically-at-runtime

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;

/**
 * Useful class for dynamically changing the classpath, adding classes during
 * runtime.
 */
public class ClassPathHacker {
	/**
	 * Parameters of the method to add an URL to the System classes.
	 */
	private static final Class<?>[] parameters = new Class[] { URL.class };

	/**
	 * Adds a file to the classpath.
	 * 
	 * @param s a String pointing to the file
	 * @throws IOException
	 */
	public static void addFile(String s) throws IOException {
		File f = new File(s);
		addFile(f);
	}

	/**
	 * Adds a file to the classpath
	 * 
	 * @param f the file to be added
	 * @throws IOException
	 */
	public static void addFile(File f) throws IOException {
		addURL(f.toURI().toURL());
	}

	/**
	 * Adds the content pointed by the URL to the classpath.
	 * 
	 * @param u the URL pointing to the content to be added
	 * @throws IOException
	 */
	public static void addURL(URL u) throws IOException {
		URLClassLoader sysloader = (URLClassLoader) ClassLoader
				.getSystemClassLoader();
		Class<?> sysclass = URLClassLoader.class;
		try {
			java.lang.reflect.Method method = sysclass.getDeclaredMethod("addURL", parameters);
			method.setAccessible(true);
			method.invoke(sysloader, new Object[] { u });
		} catch (Throwable t) {
			t.printStackTrace();
			throw new IOException(
					"Error, could not add URL to system classloader");
		}
	}

//	public static void main(String args[]) throws IOException,
//			SecurityException, ClassNotFoundException,
//			IllegalArgumentException, InstantiationException,
//			IllegalAccessException, java.lang.reflect.InvocationTargetException,
//			NoSuchMethodException {
//		addFile("C:\\dynamicloading.jar");
//		java.lang.reflect.Constructor<?> cs = ClassLoader.getSystemClassLoader()
//				.loadClass("test.DymamicLoadingTest")
//				.getConstructor(String.class);
//		DymamicLoadingTest instance = (DymamicLoadingTest) cs.newInstance();
//		instance.test();
//	}
}