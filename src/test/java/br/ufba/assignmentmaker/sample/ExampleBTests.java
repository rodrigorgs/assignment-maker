package br.ufba.assignmentmaker.sample;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import br.ufba.assignmentmaker.annotations.Secret;

public class ExampleBTests {

	@Test
	public void emptyIsZero() {
		ExampleB x = new ExampleB();
		assertEquals(0, x.quantity());
	}
	
	@Secret
	@Test
	public void sumOfQuantities() {
		ExampleB x = new ExampleB();
		x.add(new Item("a", 3));
		x.add(new Item("b", 2));
		assertEquals(5, x.quantity());
	}
}
