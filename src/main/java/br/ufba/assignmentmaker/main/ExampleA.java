package br.ufba.assignmentmaker.main;

import br.ufba.assignmentmaker.annotations.MoveToPackage;
import br.ufba.assignmentmaker.annotations.ReplaceBody;
import br.ufba.assignmentmaker.annotations.Secret;

@MoveToPackage("br.ufba.poo")
public class ExampleA {

	@Secret
	public void hiddenMethod() {
		System.out.println("hidden");
	}
	
	@ReplaceBody("break;")
	public void replaceableMethod() {
		System.out.println("replace body");
	}
}
