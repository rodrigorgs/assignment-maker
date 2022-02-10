package br.ufba.assignmentmaker.main;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.stmt.BlockStmt;

import br.ufba.assignmentmaker.annotations.Assignment;
import br.ufba.assignmentmaker.annotations.ReplaceBodyWithCode;
import br.ufba.assignmentmaker.annotations.ReplaceBodyWithMethod;
import br.ufba.assignmentmaker.annotations.Remove;

public class Transformer {
	public static String transform(String sourceCode) {
		CompilationUnit cu = StaticJavaParser.parse(sourceCode);
		transform(cu);
		return cu.toString();
	}
	
	public static void transform(CompilationUnit cu) {
		cu.findAll(ClassOrInterfaceDeclaration.class).stream().forEach(c -> {
			transform(c);
		});
		
		// remove imports that refer to assignmentmaker's annotations
		cu.findAll(ImportDeclaration.class).stream().forEach(i -> {
			if (i.getNameAsString().startsWith(Remove.class.getPackageName())) {
				i.remove();
			}
		});
	}
	
	public static void transform(ClassOrInterfaceDeclaration c) {
		c.getAnnotationByClass(Assignment.class).ifPresent(annotation -> {
			annotation.remove();
		});
		
		c.findAll(FieldDeclaration.class).stream().forEach(f -> {
			if (f.getAnnotationByClass(Remove.class).isPresent()) {
				f.remove();
			}
			// TODO: implement all valid annotations
		});
		
		c.findAll(MethodDeclaration.class).stream().forEach(m -> {
			if (m.getAnnotationByClass(Remove.class).isPresent()) {
				m.remove();
			} else if (m.getAnnotationByClass(ReplaceBodyWithCode.class).isPresent()) {
				AnnotationExpr annotation = m.getAnnotationByClass(ReplaceBodyWithCode.class).get();
				String value = "";
				if (annotation instanceof SingleMemberAnnotationExpr) {
					Expression expr = ((SingleMemberAnnotationExpr)annotation).getMemberValue();
					value = ((StringLiteralExpr)expr).asString(); // we assume that it is a string literal
					System.out.println("String Value: " + value);
				}

				BlockStmt body = StaticJavaParser.parseBlock("{" + value + "}");
				m.getBody().get().replace(body);
				annotation.remove();
			}
			
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
	
	public static Expression getParameter(AnnotationExpr annotationExpr, String parameterName){
	    List<MemberValuePair>children = annotationExpr.getChildNodesByType(MemberValuePair.class);
	    for(MemberValuePair memberValuePair : children){
	        if(parameterName.equals(memberValuePair.getNameAsString())){
	            return memberValuePair.getValue();
	        }
	    }
	    return null;
	}
	
	public static void main(String[] args) throws IOException {
		String sourceCode = Files.readString(Path.of("src/main/java/br/ufba/assignmentmaker/sample/ExampleA.java"));
		String out = transform(sourceCode);
		System.out.println(out);
	}
}
