package link.infra.tailor.agent;

import java.io.File;
import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.ProtectionDomain;

public class Agent implements ClassFileTransformer {
	public static void premain(String agentArgs, Instrumentation inst) {
		agentmain(agentArgs, inst);
	}

	public static void agentmain(String agentArgs, Instrumentation inst) {
		System.out.println("tailor-agent attached!");

		// TODO: argument parsing
		inst.addTransformer(new Agent(), true);

		// TODO: filter classes?
		// Ensure that all classes are dumped, by retransforming existing loaded classes
		Class<?>[] classes = inst.getAllLoadedClasses();
		try {
			if (classes.length > 0) {
				inst.retransformClasses(classes);
			}
		} catch (UnmodifiableClassException ignored) {}

		// TODO: listen on TCP, wait for reload command
	}

	private final Path dumpPath;
	private final Path overridePath;

	public Agent() {
		dumpPath = Paths.get(".tailor/dumped");
		overridePath = Paths.get(".tailor/override");
	}

	@Override
	public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classBytes) {
		String classRelPath = classToRelPath(className);
		dumpClass(classRelPath, classBytes);

		Path overrideClassPath = overridePath.resolve(classRelPath);
		if (Files.exists(overrideClassPath)) {
			try {
				// Return the overwritten class
				return Files.readAllBytes(overrideClassPath);
			} catch (IOException e) {
				// TODO: better error handling?
				e.printStackTrace();
			}
		}
		// No changes to class
		return null;
	}

	private void dumpClass(String classRelPath, byte[] classBytes) {
		// TODO: cache dumps, so we don't redump every time?
		Path dumpClassPath = dumpPath.resolve(classRelPath);
		try {
			Files.createDirectories(dumpClassPath.getParent());
			Files.write(dumpClassPath, classBytes);
		} catch (IOException e) {
			// TODO: better error handling?
			e.printStackTrace();
		}
	}

	private String classToRelPath(String className) {
		return className.replace("/", File.separator) + ".class";
	}
}
