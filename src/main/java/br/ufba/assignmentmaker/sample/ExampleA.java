package br.ufba.assignmentmaker.sample;

import br.ufba.assignmentmaker.annotations.Assignment;
import br.ufba.assignmentmaker.annotations.ReplaceBodyWithCode;
import br.ufba.assignmentmaker.annotations.ReplaceBodyWithMethod;
import br.ufba.assignmentmaker.annotations.Remove;

/**
 * This is an example assignment, and this
 * is the statement.
 *
 */
@Assignment("example-a")
public class ExampleA {
	private ExampleADependency dep;
	
	@Remove
	public void hiddenMethod() {
		System.out.println("hidden");
	}
	
	@ReplaceBodyWithCode("System.out.println(\"change this message\");")
	public void replaceableMethod() {
		System.out.println("Hello World");
	}
	
	@ReplaceBodyWithMethod("_sum")
	public int sum(int a, int b) {
		return a + b;
	}
	
	public int _sum(int a, int b) {
		return 0;
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
