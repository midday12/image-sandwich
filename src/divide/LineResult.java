package com.tmoncorp.imageinfra.proc.divide;

public final class LineResult {
	private boolean lineSolid = false;
	private boolean lineSameWithPrevLine = false;
	
	public LineResult() {
		lineSolid = false;
		lineSameWithPrevLine = false;
	}

	public LineResult(boolean lineSolid, boolean lineSameWithPrevLine) {
		this.lineSolid = lineSolid;
		this.lineSameWithPrevLine = lineSameWithPrevLine;
	}

	public boolean isLineSolid() {
		return lineSolid;
	}

	public void setLineSolid(boolean lineSolid) {
		this.lineSolid = lineSolid;
	}

	public boolean isLineSameWithPrevLine() {
		return lineSameWithPrevLine;
	}

	public void setLineSameWithPrevLine(boolean lineSameWithPrevLine) {
		this.lineSameWithPrevLine = lineSameWithPrevLine;
	}
}
