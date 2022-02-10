package br.ufba.assignmentmaker.main;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;

import br.ufba.assignmentmaker.annotations.Assignment;

public class AssignmentPackager {

	private static final String ORGANIZATION = "ufba-poo-2022-1";

	public static void main(String[] args) throws IOException {
		Path mainPath = Path.of("src/main/java");
		Path testPath = Path.of("src/test/java");
		
		for (Path path : Arrays.asList(mainPath, testPath)) {
			Files.walk(path)
	        .filter(p -> p.toString().endsWith(".java"))
	        .forEach(p -> {
	        	try {
					processPath(path, p);
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
	        });			
		}
		
		
		

	}

	private static void processPath(Path basePath, Path path) throws IOException {
		CompilationUnit cu = StaticJavaParser.parse(Files.readString(path));
		
		cu.findAll(ClassOrInterfaceDeclaration.class).stream().forEach(c -> {
			Optional<AnnotationExpr> opt = c.getAnnotationByClass(Assignment.class);
			if (opt.isPresent()) {
				SingleMemberAnnotationExpr ann = (SingleMemberAnnotationExpr) opt.get();
				String filename = ((StringLiteralExpr)ann.getMemberValue()).getValue();
				try {
					processAssignment(basePath, filename, path, cu, c);
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}
		});
	}
	
	private static void processAssignment(Path basePath, String filename, Path path, CompilationUnit cu, ClassOrInterfaceDeclaration assignmentClass) throws IOException {
		Path dest = createProjectStructure(filename);

		StringBuffer pkgBli = new StringBuffer();
		// Each class in its file; TODO: change visibilities to public
		cu.findAll(ClassOrInterfaceDeclaration.class).stream().forEach(c -> {
			Transformer.transform(c);
			final StringBuffer relPath = new StringBuffer(basePath.toString());
			cu.getPackageDeclaration().ifPresent(pkg -> {
				relPath.append("/" + pkg.getNameAsString().replace('.', '/'));
				if (pkgBli.isEmpty()) {
					pkgBli.append("/" + pkg.getNameAsString().replace('.', '/'));
				}
			});
			relPath.append("/" + c.getNameAsString() + ".java");
			Path pathToWrite = dest.resolve(Path.of(relPath.toString()));
			try {
				createDirectoryIfNotExist(pathToWrite.getParent());
				Files.write(pathToWrite, Arrays.asList(c.toString()), StandardCharsets.UTF_8);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		});
	
		// write test
		String className = assignmentClass.getNameAsString();
		Path testPath = Path.of("src/test/java" + pkgBli + "/" + className + "Tests.java");
		Path destTestPath = dest.resolve(testPath);
		System.out.println(testPath);
		if (Files.exists(testPath)) {
			createDirectoryIfNotExist(destTestPath.getParent());
			String result = Transformer.transform(Files.readString(testPath));
			Files.write(destTestPath, Arrays.asList(result.toString()), StandardCharsets.UTF_8);
		}
		
		// transform files
		// TODO: generalize, i.e., transform all files. Maybe use mustache.java
		sed(dest.resolve("pom.xml"), "#filename#", filename);
		sed(dest.resolve(".github/workflows/classroom.yml"), "#organization#", ORGANIZATION);
		sed(dest.resolve(".github/workflows/classroom.yml"), "#filename#", filename);
		sed(dest.resolve(".github/classroom/autograding.json"), "#class#", assignmentClass.getNameAsString());
		
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
	
	private static Path createProjectStructure(String filename) throws IOException {
		Path dest = Path.of("/tmp/assignments/" + filename); 
		
		
		createDirectoryIfNotExist(dest);
		// TODO: allow parameterization of skeleton folder
		copyFolder(Path.of("src/main/resources/skel"), dest);
		
		return dest;
	}
	
	private static void createDirectoryIfNotExist(Path dest) {
		try {
			Files.createDirectories(dest);
		} catch (DirectoryNotEmptyException e) {
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}


//NormalAnnotationExpr annotation = (NormalAnnotationExpr)c.getAnnotationByClass(Assignment.class).get();
//ArrayInitializerExpr classes = (ArrayInitializerExpr) Transformer.getParameter(annotation, "includeClasses");
//NodeList<Expression> values = classes.getValues();
//	ClassExpr klass = (ClassExpr)expr;
//	String className = klass.getTypeAsString();
//}
