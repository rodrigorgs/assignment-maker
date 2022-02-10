package br.ufba.assignmentmaker.sample;

import java.util.ArrayList;

import br.ufba.assignmentmaker.annotations.Assignment;
import br.ufba.assignmentmaker.annotations.ReplaceBodyWithCode;
import br.ufba.assignmentmaker.annotations.ReplaceBodyWithMethod;
import br.ufba.assignmentmaker.annotations.Remove;

@Assignment("example-b")
public class ExampleB {
	@Remove
	private ArrayList<Item> items = new ArrayList<>();
	
	@ReplaceBodyWithCode()
	public void add(Item item) {
		boolean found = false;
		for (Item other : items) {
			if (other.equals(item)) {
				other.setAmount(other.getAmount() + item.getAmount());
				found = true;
			}
		}
		
		if (!found) {
			items.add(item);
		}
	}
	
	@ReplaceBodyWithMethod("_quantity")
	public int quantity() {
		return items.stream().map(i -> i.getAmount()).reduce(0, (acum, x) -> acum + x);
	}
	
	public int _quantity() {
		return 0;
	}
}

class Item {
	private String name;
	private int amount;
	
	public Item(String name) {
		this(name, 1);
	}

	public Item(String name, int amount) {
		super();
		this.name = name;
		this.amount = amount;
	}

	String getName() {
		return name;
	}

	int getAmount() {
		return amount;
	}

	void setAmount(int amount) {
		this.amount = amount;
	}

	@Remove
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		return result;
	}

	@Remove
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Item other = (Item) obj;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		return true;
	}
}