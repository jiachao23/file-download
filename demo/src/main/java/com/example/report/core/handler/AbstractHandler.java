package com.example.report.core.handler;

public abstract class AbstractHandler implements DataHandler {
	private DataHandler next;

	@Override
	public void setNext(DataHandler next) {
		this.next = next;
	}

	@Override
	public void handle(RenderContext context) {
		doHandle(context);
		if (next != null) {
			next.handle(context);
		}
	}

	protected abstract void doHandle(RenderContext context);
}