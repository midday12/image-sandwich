/**
 * 
 */
package com.tmoncorp.imageinfra.proc.divide;

/**
 * Created by midday on 2015-07-01
 * 높이가 긴 이미지를, 경계선을 찾아 자동으로 잘라주는 클래스
 */

import com.tmoncorp.imageinfra.proc.FIRect;
import com.tmoncorp.imageinfra.io.ImageT;
import lombok.Getter;
import lombok.Setter;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Getter
@Setter
public class Divider {
	private int imgMinHeight = 100;						// 잘리는 이미지의 최소단위. 0보다 커야 한다.
	private int imgMaxHeight = 2000;						// 잘리는 이미지의 최대단위. 이 값에 도달하면 무조건 자른다. 0보다 커야한다.
	private int imgMinBottom = 200;                         // 마지막 남은 크기가 imgMinBotton보다 작으면, 자르지 않는다. 0보다 커야 한다.
	private int imgDitherLevel = 16;						// 픽셀 디더링 레벨. 0보다 커야 한다.
	private double imgLineThresHold = 0.9;				    // 한 라인에 같은 색의 비율이 이보다 크면, Solid Line으로 인식한다. 0보다 커야 한다.
	
	private boolean ignoreMinHeightAtFirst = true;          // 첫 분석 때 imgMinHeight를 적용할지 말지 여부.
															// 일반적으로 첫 이미지에 로고나 설명이 있는 경우가 많고, 첫 이미지가 작으면 사용자에게 초기로딩속도가 빨라보이는 장점이 있다.

	private int bufferHeight = 480;                         // 내부 이미지 처리 버퍼 크기
	private int bufferY = -1;                               // 내부 이미지 처리 버퍼의 현재 위치

	private enum SEEK_STATUS { SEEK_IN, SEEK_MARGIN };

	private InputStream fiStream = null;

	public Divider() {
	}

	public Divider(Integer _img_min_height, Integer _img_max_height, Integer _img_min_bottom, Integer _img_dither_level, Double _img_line_threshold, Boolean _ignore_min_height_at_first) {
		imgMinHeight = _img_min_height;
		imgMaxHeight = _img_max_height;
		imgMinBottom = _img_min_bottom;
		imgDitherLevel = _img_dither_level;
		imgLineThresHold = _img_line_threshold;
		ignoreMinHeightAtFirst = _ignore_min_height_at_first;
	}

	public void config(int _img_min_height, int _img_max_height) {
		this.imgMinHeight = _img_min_height;
		this.imgMaxHeight = _img_max_height;
	}

	public void config(int _img_min_height, int _img_max_height, int _img_line_threshold) {
		this.imgMinHeight = _img_min_height;
		this.imgMaxHeight = _img_max_height;
		this.imgLineThresHold = _img_line_threshold;
	}
	
	public List<FIRect> processImage(String filename) throws Exception {
		BufferedImage bi = new ImageT().read(filename);
		return processImage(bi);
	}

	public List<FIRect> processImage(BufferedImage bi) throws Exception {
		List<FIRect> rectList = new ArrayList<>();

		if(bi != null) {
			List<Integer> cut_array = processVertical(bi);
			if(cut_array.size() == 0) {
				rectList.add(new FIRect(0, 0, bi.getWidth(), bi.getHeight()));
			} else if(cut_array.size() >= 1) {
				int y = 0;
				for(int i=0; i<cut_array.size(); i++) {
					rectList.add(new FIRect(0, y, bi.getWidth(), cut_array.get(i) - y));
					y = cut_array.get(i);
				}

				// �� ������ �κ�
				rectList.add(new FIRect(0, y, bi.getWidth(), bi.getHeight() - y));
			}
		}

		return rectList;
	}

	public void drawCutLine(BufferedImage bi, ArrayList<Integer> cutArray, Color lineCol) {
		Graphics gr = bi.getGraphics();
		gr.setColor(lineCol);

		for(int y : cutArray) {
			gr.drawLine(0,  y,  bi.getWidth(), y);
		}

		bi.flush();
	}

	private List<Integer> processVertical(BufferedImage bi) {
		List<Integer> cut_array = new ArrayList<>();

		int width = bi.getWidth();
		int height = bi.getHeight();

		SEEK_STATUS currentStatus = SEEK_STATUS.SEEK_IN;
		
		int lastMarginStart = 0;
		int[] pixels = new int[width * bufferHeight];

		int y = 0, lastCut = 0;
		if(ignoreMinHeightAtFirst == false) {
			y = imgMinHeight;
			lastCut = 0;
		}

		for(; y<height; y++) {
			prepareBuffer(bi, pixels, y, width);

			LineResult lineResult = new LineResult();
			try {
				lineResult = checkLine(pixels, y, width);
			} catch(ArrayIndexOutOfBoundsException e) {
				System.err.println(e.toString());
			}
			
			switch(currentStatus) {
				case SEEK_IN: {
					if(y - lastCut >= imgMaxHeight) {
						// y를 4의 배수로 조정해준다.
						y = y / 4 * 4;

						lastMarginStart = y;
						currentStatus = SEEK_STATUS.SEEK_MARGIN;
					} else if(height - y > imgMinBottom) {        // ���� �κ��� imgMinBottom���� ������ �Ѿ��
						if(lineResult.isLineSolid() && !lineResult.isLineSameWithPrevLine()) {
							// y를 4의 배수로 조정해준다.
							y = y / 4 * 4;

							lastMarginStart = y;
							currentStatus = SEEK_STATUS.SEEK_MARGIN;
						}
					}
				}
				break;
				
				case SEEK_MARGIN: {
					if(y - lastCut >= imgMaxHeight) {
						// y를 4의 배수로 조정해준다.
						y = y / 4 * 4;

						cut_array.add(y);
						lastCut = y;
						currentStatus = SEEK_STATUS.SEEK_IN;
					} else {
						if(!lineResult.isLineSolid()) {
							cut_array.add(lastMarginStart);
							lastCut = lastMarginStart;
							currentStatus = SEEK_STATUS.SEEK_IN;
						} else if(!lineResult.isLineSameWithPrevLine()) {
							// y를 4의 배수로 조정해준다.
							y = y / 4 * 4;

							cut_array.add(y);
							lastCut = y;
							currentStatus = SEEK_STATUS.SEEK_IN;
						}
					}

					// Cut�� �Ŀ��� �ٽ� ���� ���̸�ŭ �̵�
					if(currentStatus == SEEK_STATUS.SEEK_IN) {
						y += imgMinHeight;
					}
				}
				break;
			}
		}

		return cut_array;
	}
	
	private void prepareBuffer(BufferedImage bi, int[] pixels, int y, int width) {
		boolean needAlloc = false;
		
		if(bufferY < 0) {
			needAlloc = true;
		} else if(bufferY + bufferHeight <= y) {
			needAlloc = true;
		} else if(bufferY > y) {
			needAlloc = true;			
		}
		
		if(needAlloc) {
			// ���� line�� ���ϱ� ���ؼ�, 1�� ������ �о�� �Ѵ�. ���� y == 0 �϶��� �ش���� �ʴ´�.
			if(y > 0) {
				y--;
			}
			
			// �̹��� ���� ���̰� bufferHeight���� ���� ���, bufferHeight�� ũ�⸦ ���̿� �����ش�.
			if(y + bufferHeight > bi.getHeight()) {
				bufferHeight = bi.getHeight() - y;
			}

			bi.getRGB(0, y, width, bufferHeight, pixels, 0, width);
			bufferY = y;
		}
	}
	
	private LineResult checkLine(int[] pixels, int y, int width) {
		LineResult lineResult = new LineResult(true, y == 0 ? false : true);

		int[] colorMap = new int[imgDitherLevel];
		Arrays.fill(colorMap, 0);

		for(int i=0; i<width; i++) {
			int ditheredLevel = ditherColor(pixels[i + ((y - bufferY) * width)]);
			if(ditheredLevel < imgDitherLevel) {
				colorMap[ditheredLevel]++;
			}

			if(y - bufferY > 0 && lineResult.isLineSameWithPrevLine() &&
					equalPixel(pixels[i + ((y - bufferY) * width)], pixels[i + (((y - bufferY) - 1) * width)])) {
				lineResult.setLineSameWithPrevLine(false);
			}
		}

		int longestLineWidth = 0;
		int longestLineMap = 0;
		for (int i = 0; i < imgDitherLevel; i++) {
			if (longestLineWidth < colorMap[i]) {
				longestLineWidth = colorMap[i];
				longestLineMap = i;
			}
		}

		if (longestLineMap != imgDitherLevel - 1 && (float) longestLineWidth > (float) width * imgLineThresHold) {
			lineResult.setLineSolid(true);
		} else {
			lineResult.setLineSolid(false);
		}

		return lineResult;
	}
	
	private int ditherColor(int p) {
		byte[] pixels = ByteBuffer.allocate(4).putInt(p).array();
		
		int ditheredLevel = 0;

		for(int i=1; i<=3; i++) {						// alpha value는 무시 - JPEG는 alpha가 없으니깐...
			ditheredLevel += Byte.toUnsignedInt(pixels[i]);
		}

		ditheredLevel = (int)(((float)ditheredLevel / (float)(256 * 3)) * (float) imgDitherLevel);
		return ditheredLevel;
	}

	private boolean equalPixel(int p1, int p2) {
		return (ditherColor(p1) == ditherColor(p2));
	}
}
