package com.tmoncorp.imageinfra.proc;

/**
 * Created by midday on 2015-07-06.
 */
public class FIRect {
	public int x, y, width, height;

	public FIRect() {
		this.x = this.y = this.width = this.height = 0;
	}

	public FIRect(int x, int y, int width, int height) {
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
	}
}
