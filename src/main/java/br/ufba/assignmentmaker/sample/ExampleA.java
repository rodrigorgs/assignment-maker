package br.ufba.assignmentmaker.sample;

import br.ufba.assignmentmaker.annotations.Assignment;
import br.ufba.assignmentmaker.annotations.ReplaceBody;
import br.ufba.assignmentmaker.annotations.Secret;

/**
 * This is an example assignment, and this
 * is the statement.
 *
 */
@Assignment("example-a")
public class ExampleA {
	private ExampleADependency dep;
	
	@Secret
	public void hiddenMethod() {
		System.out.println("hidden");
	}
	
	@ReplaceBody("System.out.println(\"change this message\");")
	public void replaceableMethod() {
		System.out.println("Hello World");
	}
	
	@ReplaceBody("return 0;")
	public int sum(int a, int b) {
		return a + b;
	}
}

class ExampleADependency {
	private int x;

	int getX() {
		return x;
	}

	void setX(int x) {
		this.x = x;
	}
}
