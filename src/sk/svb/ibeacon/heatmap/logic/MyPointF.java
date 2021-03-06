package sk.svb.ibeacon.heatmap.logic;

import android.graphics.PointF;

/**
 * extended pointF with radius
 * 
 * @author mbodis
 *
 */
public class MyPointF extends PointF {
	double radius = 0;

	public MyPointF(float x, float y, double radius) {
		this.x = x;
		this.y = y;
		this.radius = radius;
	}
}
