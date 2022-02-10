package br.ufba.assignmentmaker.sample;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import br.ufba.assignmentmaker.annotations.Remove;

public class ExampleATest {

	@Test
	public void sumPositiveNumbers() {
		assertEquals(3, new ExampleA().sum(1, 2));
	}
	
	@Remove
	@Test
	public void sumNegativeNumbers() {
		assertEquals(-3, new ExampleA().sum(-1, -2));
	}	
}
