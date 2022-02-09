package br.ufba.assignmentmaker.main;

import java.util.List;
import java.util.Optional;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.stmt.BlockStmt;

import br.ufba.assignmentmaker.annotations.MoveToPackage;
import br.ufba.assignmentmaker.annotations.ReplaceBody;
import br.ufba.assignmentmaker.annotations.Secret;

public class Transformer {
	public static String transform(String sourceCode) {
		CompilationUnit cu = StaticJavaParser.parse(sourceCode);
		
		cu.findAll(MethodDeclaration.class).stream().forEach(m -> {
			if (m.getAnnotationByClass(Secret.class).isPresent()) {
				m.remove();
			} else if (m.getAnnotationByClass(ReplaceBody.class).isPresent()) {
				AnnotationExpr annotation = m.getAnnotationByClass(ReplaceBody.class).get();
				String value = "";
				if (annotation instanceof SingleMemberAnnotationExpr) {
					Expression expr = ((SingleMemberAnnotationExpr)annotation).getMemberValue();
					value = ((StringLiteralExpr)expr).getValue(); // we assume that it is a string literal
				} else {
					Expression expr = getParameter(annotation, "value");
					if (expr instanceof StringLiteralExpr) {
						value = ((StringLiteralExpr)expr).getValue();
					}
				}

				BlockStmt body = StaticJavaParser.parseBlock("{" + value + "}");
				m.getBody().get().replace(body);
				annotation.remove();
			}
		});
		
		cu.findAll(ClassOrInterfaceDeclaration.class).stream().forEach(c -> {
			Optional<AnnotationExpr> annotation = c.getAnnotationByClass(MoveToPackage.class);
			if (annotation.isPresent()) {
				StringLiteralExpr value = (StringLiteralExpr)((SingleMemberAnnotationExpr)annotation.get()).getMemberValue();
				cu.setPackageDeclaration(value.asString());
				annotation.get().remove();
			}
		});
		
		return cu.toString();
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
	
	public static void main(String[] args) {
		String sourceCode = 
				"package br.ufba.assignmentmaker.main;\n"
				+ "\n"
				+ "import br.ufba.assignmentmaker.annotations.MoveToPackage;\n"
				+ "import br.ufba.assignmentmaker.annotations.ReplaceBody;\n"
				+ "import br.ufba.assignmentmaker.annotations.Secret;\n"
				+ "\n"
				+ "@MoveToPackage(\"br.ufba.poo\")\n"
				+ "public class ExampleA {\n"
				+ "\n"
				+ "	@Secret\n"
				+ "	public void hiddenMethod() {\n"
				+ "		System.out.println(\"hidden\");\n"
				+ "	}\n"
				+ "	\n"
				+ "	@ReplaceBody(\"break;\")\n"
				+ "	public void replaceableMethod() {\n"
				+ "		System.out.println(\"replace body\");\n"
				+ "	}\n"
				+ "}\n"
				+ "";
		String out = transform(sourceCode);
		System.out.println(out);
	}
}
