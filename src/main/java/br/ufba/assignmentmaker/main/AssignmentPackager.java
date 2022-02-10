package br.ufba.assignmentmaker.main;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.apache.maven.shared.invoker.DefaultInvocationRequest;
import org.apache.maven.shared.invoker.DefaultInvoker;
import org.apache.maven.shared.invoker.InvocationRequest;
import org.apache.maven.shared.invoker.InvocationResult;
import org.apache.maven.shared.invoker.Invoker;
import org.apache.maven.shared.invoker.MavenInvocationException;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier.Keyword;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;

import br.ufba.assignmentmaker.annotations.Assignment;

public class AssignmentPackager {

	private String organizationName;
	private Path inputPath = Path.of(".");
	private Path outputPath;
	private Path skeletonPath = Path.of("src/main/resources/skel"); 
	// TODO
	private boolean shouldBuildAfterCreating = false;
	
	public AssignmentPackager(String organizationName, Path inputPath, Path outputPath) {
		super();
		this.organizationName = organizationName;
		this.inputPath = inputPath;
		this.outputPath = outputPath;
	}
	
	
	public void generatePackages() throws IOException {
		Path path = inputPath.resolve(Path.of("src/main/java"));
		
		Files.walk(path)
        .filter(p -> p.toString().endsWith(".java"))
        .forEach(p -> {
        	try {
				processPath(p);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
        });			
	}

	private void processPath(Path path) throws IOException {
		CompilationUnit cu = StaticJavaParser.parse(Files.readString(path));
		
		cu.findAll(ClassOrInterfaceDeclaration.class).stream().forEach(c -> {
			Optional<AnnotationExpr> opt = c.getAnnotationByClass(Assignment.class);
			if (opt.isPresent()) {
				SingleMemberAnnotationExpr ann = (SingleMemberAnnotationExpr) opt.get();
				String filename = ((StringLiteralExpr)ann.getMemberValue()).asString();
				try {
					processAssignment(filename, cu, c);
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}
		});
	}
	
	private void processAssignment(String filename, CompilationUnit cu, ClassOrInterfaceDeclaration assignmentClass) throws IOException {
		Path dest = createProjectStructure(filename);

		final String pkgPath;
		Optional<PackageDeclaration> pkgDeclaration = cu.getPackageDeclaration();
		if (pkgDeclaration.isPresent()) {
			pkgPath = "/" + pkgDeclaration.get().getNameAsString().replace('.', '/');
		} else {
			pkgPath = "";
		}
		
		// Each class in its file
		cu.findAll(ClassOrInterfaceDeclaration.class).stream().forEach(c -> {
			Transformer.transform(c);
			c.setModifier(Keyword.PUBLIC, true);
			
			Path relPath = Path.of("src/main/java" + pkgPath + "/" + c.getNameAsString() + ".java");
			Path pathToWrite = dest.resolve(relPath);
			try {
				createDirectoryIfNotExists(pathToWrite.getParent());
				ArrayList<String> lines = new ArrayList<>();
				if (!pkgPath.isEmpty()) {
					lines.add("package " + pkgPath.substring(1).replace('/', '.') + ";\n");
				}
				lines.add(c.toString());
				Files.write(pathToWrite, lines, StandardCharsets.UTF_8);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		});
	
		// write test
		String className = assignmentClass.getNameAsString();
		Path testPath = Path.of("src/test/java" + pkgPath + "/" + className + "Tests.java");
		Path destTestPath = dest.resolve(testPath);
		System.out.println(testPath);
		if (Files.exists(testPath)) {
			createDirectoryIfNotExists(destTestPath.getParent());
			String result = Transformer.transform(Files.readString(testPath));
			Files.write(destTestPath, Arrays.asList(result.toString()), StandardCharsets.UTF_8);
		}
		
		// transform files
		// TODO: generalize, i.e., transform all files. Maybe use mustache.java
		sed(dest.resolve("pom.xml"), "#filename#", filename);
		sed(dest.resolve(".github/workflows/classroom.yml"), "#organization#", organizationName);
		sed(dest.resolve(".github/workflows/classroom.yml"), "#filename#", filename);
		sed(dest.resolve(".github/classroom/autograding.json"), "#class#", assignmentClass.getNameAsString());
		
		if (shouldBuildAfterCreating) {
			InvocationRequest request = new DefaultInvocationRequest();
			request.setPomFile(dest.resolve("pom.xml").toFile());
			request.setGoals(Collections.singletonList("install"));
			 
			Invoker invoker = new DefaultInvoker();
//			invoker.setMavenHome(Path.of("/opt/homebrew/bin/mvn").getParent().toFile());
			invoker.setMavenHome(new File("/opt/homebrew"));
			try {
				InvocationResult result = invoker.execute(request);
				if (result.getExitCode() != 0) {
					System.out.println("Build failed for " + filename);
				} else {
					System.out.println("Build successful for " + filename);
				}
			} catch (MavenInvocationException e) {
				throw new RuntimeException(e);
			}
		}
		
		// TODO: create solution project
		// TODO: create test project
		// TODO: create git repo, already with remote info, ready for pushing into github
	}

	public static void sed(Path path, String str, String replacement) {
		try {
			String contents = Files.readString(path);
			contents = contents.replaceAll(Pattern.quote(str), replacement);
			Files.write(path, Arrays.asList(contents), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	public static void copyFolder(Path src, Path dest) throws IOException {
	    try (Stream<Path> stream = Files.walk(src)) {
	        stream.forEach(source -> copy(source, dest.resolve(src.relativize(source))));
	    }
	}

	private static void copy(Path source, Path dest) {
	    try {
	        Files.copy(source, dest, REPLACE_EXISTING);
	    } catch (Exception e) {
	        throw new RuntimeException(e.getMessage(), e);
	    }
	}
	
	private Path createProjectStructure(String filename) throws IOException {
		Path dest = outputPath.resolve(filename); 
		
		createDirectoryIfNotExists(dest);
		copyFolder(skeletonPath, dest);
		
		return dest;
	}
	
	private static void createDirectoryIfNotExists(Path dest) {
		try {
			Files.createDirectories(dest);
		} catch (DirectoryNotEmptyException e) {
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public String getOrganizationName() {
		return organizationName;
	}
	
	public void setOrganizationName(String organizationName) {
		this.organizationName = organizationName;
	}
	
	public Path getOutputPath() {
		return outputPath;
	}
	
	public void setOutputPath(Path outputPath) {
		this.outputPath = outputPath;
	}

	public Path getSkeletonPath() {
		return skeletonPath;
	}

	public void setSkeletonPath(Path skeletonPath) {
		this.skeletonPath = skeletonPath;
	}

	public boolean isShouldBuildAfterCreating() {
		return shouldBuildAfterCreating;
	}

	public void setShouldBuildAfterCreating(boolean shouldBuildAfterCreating) {
		this.shouldBuildAfterCreating = shouldBuildAfterCreating;
	}

	public static void main(String[] args) throws IOException {
		AssignmentPackager packager = new AssignmentPackager("ufba-poo-2022-1", Path.of("."), Path.of("/tmp/assignments/"));
		packager.setShouldBuildAfterCreating(true);
		packager.generatePackages();
	}
}
