package link.infra.tailor.agent;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Agent implements ClassFileTransformer {
	public static void premain(String agentArgs, Instrumentation inst) {
		agentmain(agentArgs, inst);
	}

	public static void agentmain(String agentArgs, Instrumentation inst) {
		System.out.println("tailor-agent attached!");

		// TODO: argument parsing
		inst.addTransformer(new Agent(), true);

		// TODO: filter classes?
		retransformExisting(inst);

		// TODO: listen on TCP, wait for reload command
		startHotloadThread(inst);
	}

	private static void retransformExisting(Instrumentation inst) {
		// Ensure that all classes are dumped, by retransforming existing loaded classes
		Class<?>[] classes = inst.getAllLoadedClasses();
		try {
			if (classes.length > 0) {
				inst.retransformClasses(classes);
			}
		} catch (UnmodifiableClassException ignored) {}
	}

	private static void startHotloadThread(Instrumentation inst) {
		new Thread(() -> {
			try {
				ServerSocket server = new ServerSocket(19303);
				while (true) {
					Socket clientSocket = server.accept();
					new Thread(() -> {
						List<Class<?>> classReloadList = new ArrayList<>();
						Class<?>[] classes = inst.getAllLoadedClasses();
						try (BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()))) {
							String classToReload;
							while ((classToReload = reader.readLine()) != null) {
								// TODO: lambda names are dynamic?
								boolean matched = false;
								for (Class<?> clazz : classes) {
									if (classToReload.equals(clazz.getName())) {
										classReloadList.add(clazz);
										matched = true;
										break;
									}
								}
								if (!matched) {
									System.out.println("[tailor-agent] Failed to match class " + classToReload);
								}
							}
						} catch (IOException e) {
							// TODO: better error handling
							e.printStackTrace();
						}
						String[] classReloadNames = classReloadList.stream().map(Class::getName).toArray(String[]::new);
						System.out.println("[tailor-agent] Reloading classes: " + Arrays.toString(classReloadNames));
						try {
							inst.retransformClasses(classReloadList.toArray(new Class<?>[0]));
						} catch (UnmodifiableClassException e) {
							// TODO: better error logging
							e.printStackTrace();
						}
					}, "tailor-agent Hotload socket handler").start();
				}
			} catch (IOException e) {
				// TODO: better error logging
				e.printStackTrace();
			}
		}, "tailor-agent Hotload server").start();
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
