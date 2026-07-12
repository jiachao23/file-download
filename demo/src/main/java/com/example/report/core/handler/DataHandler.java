package com.example.report.core.handler;

public interface DataHandler {
	void handle(RenderContext context);
	void setNext(DataHandler next);
}