package br.ufba.assignmentmaker.main;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.stmt.BlockStmt;

import br.ufba.assignmentmaker.annotations.Assignment;
import br.ufba.assignmentmaker.annotations.Remove;
import br.ufba.assignmentmaker.annotations.ReplaceBodyWithCode;
import br.ufba.assignmentmaker.annotations.ReplaceBodyWithMethod;

public class Transformer {
	public String transform(String sourceCode) {
		CompilationUnit cu = StaticJavaParser.parse(sourceCode);
		transform(cu);
		return cu.toString();
	}
	
	public void transform(CompilationUnit cu) {
		cu.findAll(ClassOrInterfaceDeclaration.class).stream().forEach(c -> {
			transform(c);
		});
		
		removeAnnotationImports(cu);
	}

	public void removeAnnotationImports(CompilationUnit cu) {
		// remove imports that refer to assignmentmaker's annotations
		cu.findAll(ImportDeclaration.class).stream().forEach(i -> {
			if (i.getNameAsString().startsWith(Remove.class.getPackageName())) {
				i.remove();
			}
		});
	}
	
	public void removeAssignmentAnnotations(CompilationUnit cu) {
		cu.findAll(ClassOrInterfaceDeclaration.class).forEach(c -> {
			removeAssignmentAnnotations(c);
		});
	}
	
	public void removeAssignmentAnnotations(ClassOrInterfaceDeclaration c) {
		List<String> annotationsToRemove = Arrays.asList("Assignment", "Remove", "ReplaceBodyWithCode", "ReplaceBodyWithMethod");
		c.findAll(AnnotationExpr.class).stream().forEach(a -> {
			if (annotationsToRemove.contains(a.getNameAsString())) {
				a.remove();
			}
		});
	}
	
	private void processRemoveAnnotation(BodyDeclaration<?> elem) {
		elem.getAnnotationByClass(Remove.class).ifPresent(annotation -> {
			elem.remove();
		});
	}
	
	public void transform(ClassOrInterfaceDeclaration c) {
		
		processRemoveAnnotation(c);
		c.getAnnotationByClass(Assignment.class).ifPresent(annotation -> {
			annotation.remove();
		});
		
		c.findAll(FieldDeclaration.class).stream().forEach(f -> {
			processRemoveAnnotation(f);
		});
		
		c.findAll(ConstructorDeclaration.class).stream().forEach(cons -> {
			processRemoveAnnotation(cons);
		});
		
		c.findAll(MethodDeclaration.class).stream().forEach(m -> {
			processRemoveAnnotation(m);
			
			m.getAnnotationByClass(ReplaceBodyWithCode.class).ifPresent(annotation -> {
				String value = "";
				if (annotation instanceof SingleMemberAnnotationExpr) {
					Expression expr = ((SingleMemberAnnotationExpr)annotation).getMemberValue();
					value = ((StringLiteralExpr)expr).asString(); // we assume that it is a string literal
				}

				BlockStmt body = StaticJavaParser.parseBlock("{" + value + "}");
				m.getBody().get().replace(body);
				annotation.remove();
			});
			
			m.getAnnotationByClass(ReplaceBodyWithMethod.class).ifPresent(annotation -> {
				String methodName = ((SingleMemberAnnotationExpr)annotation).getMemberValue().asStringLiteralExpr().asString();
				List<MethodDeclaration> methodList = c.getMethodsByName(methodName);
				if (methodList.size() != 1) {
					throw new RuntimeException("There should be exactly one method with name " + methodName);
				}
				MethodDeclaration otherMethod = methodList.get(0);
				m.setBody(otherMethod.getBody().get());
				otherMethod.remove();
				annotation.remove();
			});
			
		});
	}
	
//	private static Expression getParameter(AnnotationExpr annotationExpr, String parameterName){
//	    List<MemberValuePair>children = annotationExpr.getChildNodesByType(MemberValuePair.class);
//	    for(MemberValuePair memberValuePair : children){
//	        if(parameterName.equals(memberValuePair.getNameAsString())){
//	            return memberValuePair.getValue();
//	        }
//	    }
//	    return null;
//	}
	
	public static void main(String[] args) throws IOException {
		Transformer t = new Transformer();
		String sourceCode = Files.readString(Path.of("src/main/java/br/ufba/assignmentmaker/sample/ExampleA.java"));
		String out = t.transform(sourceCode);
		System.out.println(out);
	}
}
