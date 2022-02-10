package br.ufba.assignmentmaker.main;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.apache.commons.io.output.StringBuilderWriter;
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
import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;

import br.ufba.assignmentmaker.annotations.Assignment;

public class AssignmentPackager {

	private String organizationName;
	private Path inputPath = Path.of(".");
	private Path outputPath;
	private Path skeletonPath = Path.of("src/main/resources/skel"); 
	private boolean shouldBuildAfterCreating = false;
	private boolean replaceExistingOutputFolder = false;
	
	private Transformer transformer = new Transformer();
	private MustacheFactory mf = new DefaultMustacheFactory();
	
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
		Path assignmentPath = createProjectStructure(filename + "--assignment");
		Path solutionPath = createProjectStructure(filename + "--solution");
		
		final String pkgPath = extractPackagePath(cu);
		
		// substitute template variables files
		TemplateScope scope = new TemplateScope();
		scope.filename = filename;
		scope.organizationName = organizationName;
		scope.className = assignmentClass.getNameAsString();
		substituteTemplateVariables(assignmentPath, scope);
		substituteTemplateVariables(solutionPath, scope);

		
		// Each class in its file
		// TODO: all files share the same imports...
		cu.findAll(ClassOrInterfaceDeclaration.class).stream().forEach(c -> {
			c.setModifier(Keyword.PUBLIC, true);
			
			// TODO: refactor duplicated code
			// solution
			CompilationUnit cuSolution = new CompilationUnit();
			cu.getPackageDeclaration().ifPresent(p -> { cuSolution.setPackageDeclaration(p); });
			cuSolution.addType(c);
			cu.getImports().forEach(i -> { cuSolution.addImport(i); });
			transformer.removeAnnotationImports(cuSolution);
			transformer.removeAssignmentAnnotations(cuSolution);
			writeCompilationUnit(cuSolution, solutionPath, c);
			
			// assignment
			CompilationUnit cuAssignment = new CompilationUnit();
			cu.getPackageDeclaration().ifPresent(p -> { cuAssignment.setPackageDeclaration(p); });
			cuAssignment.addType(c);
			cu.getImports().forEach(i -> { cuAssignment.addImport(i); });
			ClassOrInterfaceDeclaration classAssignment = cuAssignment.getClassByName(c.getNameAsString()).get(); 
			transformer.transform(classAssignment);
			transformer.removeAnnotationImports(cuAssignment);
			writeCompilationUnit(cuAssignment, assignmentPath, c);
		});
	
		// write test (assignment)
		String className = assignmentClass.getNameAsString();
		Path testCasePath = Path.of("src/test/java" + pkgPath + "/" + className + "Tests.java");
		
		if (Files.exists(testCasePath)) {
			// assignment
			Path assignmentTestPath = assignmentPath.resolve(testCasePath);
			createDirectoryIfNotExists(assignmentTestPath.getParent());
			CompilationUnit cuAssignmentTestCase = StaticJavaParser.parse(testCasePath);
			transformer.transform(cuAssignmentTestCase);
			Files.write(assignmentTestPath, Arrays.asList(cuAssignmentTestCase.toString()), StandardCharsets.UTF_8);
			
			// solution
			Path solutionTestPath = solutionPath.resolve(testCasePath);
			createDirectoryIfNotExists(solutionTestPath.getParent());
			CompilationUnit cuSolutionTestCase = StaticJavaParser.parse(testCasePath);
			transformer.removeAnnotationImports(cuSolutionTestCase);
			transformer.removeAssignmentAnnotations(cuSolutionTestCase);
			Files.write(solutionTestPath, Arrays.asList(cuSolutionTestCase.toString()), StandardCharsets.UTF_8);
		}
		
		// write test (solution)
				
		if (shouldBuildAfterCreating) {
			buildWithMaven(filename, assignmentPath);
			buildWithMaven(filename, solutionPath);
		}

		// Create project with all tests (including tests that were hidden from students)
		Path testProjPath = outputPath.resolve(filename + "--tests");
		rm_rf(testProjPath); // TODO: check boolean 
		createDirectoryIfNotExists(testProjPath);
		Files.copy(testCasePath, testProjPath.resolve(className + "Tests.java"), REPLACE_EXISTING);
		
		// TODO: create git repo, already with remote info, ready for pushing into github
	}


	private String extractPackagePath(CompilationUnit cu) {
		final String pkgPath;
		Optional<PackageDeclaration> pkgDeclaration = cu.getPackageDeclaration();
		if (pkgDeclaration.isPresent()) {
			pkgPath = "/" + pkgDeclaration.get().getNameAsString().replace('.', '/');
		} else {
			pkgPath = "";
		}
		return pkgPath;
	}

	private void writeCompilationUnit(CompilationUnit cu, Path dest, ClassOrInterfaceDeclaration c) {
		String pkgPath = extractPackagePath(cu);
		Path relPath = Path.of("src/main/java" + pkgPath + "/" + c.getNameAsString() + ".java");
		Path pathToWrite = dest.resolve(relPath);
		try {
			createDirectoryIfNotExists(pathToWrite.getParent());
			Files.write(pathToWrite, Arrays.asList(cu.toString()), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private void writeClass(CompilationUnit cu, Path dest, final String pkgPath, ClassOrInterfaceDeclaration c) {
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
	}

	private void substituteTemplateVariables(Path dest, TemplateScope scope) throws IOException {
		Files.walk(dest).filter(path -> !Files.isDirectory(path)).forEach(path -> {
			try {
				String contents = Files.readString(path);
				// Change delimiter to [[ ]]
				Mustache mustache = mf.compile(new StringReader("{{=[[ ]]=}}" + contents), "seila");
				StringBuilderWriter writer = new StringBuilderWriter();
				mustache.execute(writer, scope);
				String result = writer.toString();
				writer.close();
				if (!contents.equals(result)) {
					Files.write(path, Arrays.asList(result), StandardCharsets.UTF_8);
				}
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		});
	}

	private void buildWithMaven(String filename, Path dest) {
		InvocationRequest request = new DefaultInvocationRequest();
		request.setPomFile(dest.resolve("pom.xml").toFile());
		request.setGoals(Collections.singletonList("install"));
		 
		Invoker invoker = new DefaultInvoker();
		String mavenHome = System.getenv("M3_HOME");
		if (mavenHome == null) {
			mavenHome = System.getenv("M2_HOME");
		}
		if (mavenHome == null) {
			mavenHome = "/opt/homebrew";
		}
		invoker.setMavenHome(new File(mavenHome));
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
		if (replaceExistingOutputFolder && dest.toFile().exists()) {
			rm_rf(dest);
		}
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

	private static boolean rm_rf(Path pathToBeDeleted) {
	    try {
			Files.walk(pathToBeDeleted)
			  .sorted(Comparator.reverseOrder())
			  .map(Path::toFile)
			  .forEach(File::delete);
			return true;
		} catch (IOException e) {
			return false;
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

	public boolean isReplaceExistingOutputFolder() {
		return replaceExistingOutputFolder;
	}

	public void setReplaceExistingOutputFolder(boolean deleteDestinationIfExists) {
		this.replaceExistingOutputFolder = deleteDestinationIfExists;
	}

	public static void main(String[] args) throws IOException {
		AssignmentPackager packager = new AssignmentPackager("ufba-poo-2022-1", Path.of("."), Path.of("/tmp/assignments/"));
		packager.setShouldBuildAfterCreating(true);
		packager.setReplaceExistingOutputFolder(true);
		packager.generatePackages();
	}

}

class TemplateScope {
	public String filename;
	public String organizationName;
	public String className;
	public String getFilename() {
		return filename;
	}
	public void setFilename(String filename) {
		this.filename = filename;
	}
	public String getOrganizationName() {
		return organizationName;
	}
	public void setOrganizationName(String organizationName) {
		this.organizationName = organizationName;
	}
	public String getClassName() {
		return className;
	}
	public void setClassName(String className) {
		this.className = className;
	}
}
