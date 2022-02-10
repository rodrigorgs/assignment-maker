package br.ufba.assignmentmaker.sample;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import br.ufba.assignmentmaker.annotations.Secret;

public class ExampleBTests {

	@Test
	public void sumPositiveNumbers() {
		assertEquals(3, new ExampleA().sum(1, 2));
	}
	
	@Secret
	@Test
	public void sumNegativeNumbers() {
		assertEquals(-3, new ExampleA().sum(-1, -2));
	}	
}
