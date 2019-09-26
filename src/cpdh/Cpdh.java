package cpdh;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.PriorityQueue;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;


public class Cpdh {
	
	private static final List<String> GROUP_NAMES = getGroupNames(); // List of names of groups from MPEG-7 shape data set.
	private static final Mat COST_MATRIX = generateCostMat(36, 3);
	
//	private final int numOfPoints;
	private final int[] histogram;
	private final Mat signature;
	private final List<Image> images;
	private boolean isPartOfDataSet; // Infer from file name.  
	private int position; // Position in group in data set. Derived from file name. If not part of data set then = -1;
	private final File file;
	
	private static List<String> getGroupNames() { 

		return List.of("apple", "bat", "beetle", "bell", "bird", "Bone", "bottle", "brick", "butterfly",
				"camel", "car", "carriage", "cattle", "cellular_phone", "chicken", "children", "chopper",
				"classic", "Comma", "crown", "cup", "deer", "device0", "device1", "device2", "device3",
				"device4", "device5", "device6", "device7", "device8", "device9", "dog", "elephant", "face",
				"fish", "flatfish", "fly", "fork", "fountain", "frog", "Glas", "guitar", "hammer", "hat",
				"HCircle", "Heart", "horse", "horseshoe", "jar", "key", "lizzard", "lmfish", "Misk",
				"octopus", "pencil", "personal_car", "pocket", "rat", "ray", "sea_snake", "shoe", "spoon",
				"spring", "stef", "teddy", "tree", "truck", "turtle", "watch");
	}
	
	static private Mat generateCostMat( int histLength , int numOfCircles) {
	
		int elementsInCircle = histLength / numOfCircles;
		int maxDist1 = elementsInCircle / 2;   // maximal distance inside a circle (rounded down)
		
		Mat DIST_COST = new Mat(histLength, histLength, CvType.CV_32FC1);
		
		Mat[][] blocks = new Mat[numOfCircles][numOfCircles];
		
		// define blocks
		Rect roi = new Rect(0, 0, elementsInCircle, elementsInCircle) ;
		
		for (int row = 0; row < numOfCircles; row++) {
			for (int col = 0; col < numOfCircles; col++) {
				
				roi.x = col * elementsInCircle;
				roi.y = row * elementsInCircle;
				blocks[row][col] = DIST_COST.submat(roi);
			}
		}
		
		// calculate 1st block
		Mat block00 = blocks[0][0];
		//float [] distanceArray1st = new float[blok00.rows() * blok00.cols()];
		float [][] distanceBlock = new float [block00.rows()] [block00.cols()];
		
		// first block
		for (int row = 0; row < block00.rows(); row++) {
			for (int col = 0; col < block00.cols(); col++) {
	
				//distanceArray1st[row * block00.cols() + (row + col) % elementsInCircle] = maxDist1 - Math.abs(maxDist1 - col);
				
				distanceBlock [row] [(row + col) % block00.cols()] = maxDist1 - Math.abs(maxDist1 - col);
			}
			block00.put(row, 0, distanceBlock [row] );
		}
		
		// fill in rest of the blocks
		Scalar circleDiference = new Scalar(0); // 0 - in same circle; 1 - 1 circle distance ... 
		for(int row = 0; row < blocks.length; row ++) {
			for(int col = 0; col < blocks[0].length; col ++) {
				
				circleDiference.val[0] =  (Math.abs(row - col));
				Core.add(block00, circleDiference, blocks [row] [col]);
			}
		}
		return DIST_COST;
	}

	public Cpdh(File file, int numOfPoints) {

		this(file, numOfPoints, true);
	}

	public Cpdh(File file, int numOfPoints, boolean saveImages) {
		
		this.file = file;
		ResultsContainer result = processFile(file, numOfPoints, saveImages);
		this.histogram = result.histogram;
		this.signature = toSignature(result.histogram);
		this.images = result.getImages();
		try {
			this.isPartOfDataSet = checkIfPartOfDataSet(file);
			this.position = isPartOfDataSet ? readPosition(file)
											: -1;
		} catch (Exception e) {
			this.isPartOfDataSet = false;
			this.position = -1;
		}
	}
	
	public Cpdh (String s) {
		
		int[] histogram = new int [36];  // 3 x 12 regions
		String[] value = s.split("\\s+");
		
		for (int bin = 0; bin < histogram.length; bin++) {
			histogram[bin] = Integer.parseInt(value[bin]);
		}
		this.histogram 	= histogram;
		this.images		= List.of();
		this.signature 	= toSignature(histogram);
		this.file		= null;
	}

	private ResultsContainer processFile(File file, int numOfPoints, boolean saveImages) {

		Mat matSrc = Imgcodecs.imread(file.getAbsolutePath());

		if ( !matSrc.empty()) {

			ResultsContainer result = processImage(matSrc, numOfPoints, saveImages);
			calculateCpdh(result);

			return result;
		}
		else
			throw new IllegalArgumentException("Unable to read file: " + file.getName());
	}

	private ResultsContainer processImage(Mat matSrc, int numOfPoints, boolean saveImages) {
		
		/* Loaded image -> Grayscale -> Binary -> Sampling (-> Circle -> Regions)*/

		/* convert to grayscale */

		Mat matGray = new Mat(matSrc.size(), CvType.CV_8UC1);
		Imgproc.cvtColor(matSrc, matGray, Imgproc.COLOR_BGR2GRAY);

		/* convert to binary */

		Mat matBinary = new Mat(matGray.size(), CvType.CV_8UC1);
		Imgproc.threshold(matGray, matBinary, 235, 255, Imgproc.THRESH_BINARY);
		
		/*  background check  */ 
	
		boolean backgroundIsBlack = determineBackground(matBinary);
		
		if (!backgroundIsBlack) {
			Core.bitwise_not(matBinary, matBinary);
		}
		
		/* extract points on contour(s) */
		
		ArrayList <MatOfPoint> contours = new ArrayList <MatOfPoint>();
		Imgproc.findContours(matBinary, contours, new Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_NONE);
		
		/* sampling */

		Point [] sampledPoints = sampling (contours, numOfPoints);

		/* calculate minimal enclosing circle */

		var matPoints2f = new MatOfPoint2f();
		matPoints2f.fromArray(sampledPoints);
		Point center = new Point();
		float[] radiusAsArray = new float[1]; //
		Imgproc.minEnclosingCircle(matPoints2f, center, radiusAsArray);
		float radius = radiusAsArray[0];
		
		/*Pack results */
		
		ResultsContainer result = new ResultsContainer();
		result.sampledPoints = sampledPoints;
		result.center = center;
		result.radius = radius;

		/* pack all images */

		if (saveImages) {
			
			Mat matContour = Mat.zeros(matBinary.size(), CvType.CV_8UC3);
			Imgproc.drawContours(matContour, contours, -1 /*draw all*/, new Scalar(255, 255, 255));
			
			Mat matSampledPoints = Mat.zeros(matSrc.size(), CvType.CV_8UC1);
			drawPoints(matSampledPoints, sampledPoints, 1);
			
			Mat matCircle = matSampledPoints.clone();
//			int radiusInt = (int) Math.round(radius);
			int radiusInt = (int) Math.round(Math.ceil(radius)); // rounding up so every pixel is captured (drawCircle() uses int)
			Imgproc.circle(matCircle, center, radiusInt, new Scalar (255), 1);
			
			Mat matRegions = Mat.zeros(matSampledPoints.size(), CvType.CV_8SC3);
			drawRegions(matRegions, center, radius, sampledPoints);
			
			result.matImages.add(matSrc);
//			images.images.add(matGray);					not displayed
			result.matImages.add(matBinary);
			result.matImages.add(matContour);
			result.matImages.add(matSampledPoints);
			result.matImages.add(matCircle);
			result.matImages.add(matRegions);
		}
		return result;
	}
	
	private boolean determineBackground(Mat matBinary) {
		
		/* black = 0 ; white = -1  ( as a result of unsigned byte to signed conversion ) */
		
		boolean backgroundIsBlack;
		
		byte[] bckTopLeft = new byte[1];
		byte[] bckTopRight = new byte[1];
		byte[] bckBottomLeft = new byte[1];
		byte[] bckBottommRight = new byte[1];
		
		/* Sample corners */
		matBinary.get(0, 0, bckTopLeft);
		matBinary.get(0, matBinary.cols() - 1, bckTopRight);
		matBinary.get(matBinary.rows() - 1, 0, bckBottomLeft);
		matBinary.get(matBinary.rows() - 1, matBinary.cols() - 1, bckBottommRight);

		if (bckTopLeft[0] == 0 && bckBottommRight[0] == 0)
			backgroundIsBlack = true;
		else if (bckTopRight[0] == 0 && bckBottomLeft[0] == 0) 
			backgroundIsBlack = true;
		else 
			backgroundIsBlack = false;

		return backgroundIsBlack;
	}

	private Point [] sampling(List<MatOfPoint> contours, int numOfPoints) {
		
		/* 
		 * All points from contours.
		 *  (There is only outer contour in contours)
		 */
		ArrayList<Point> allPoints = new ArrayList<Point>(4096);
		for (MatOfPoint contour : contours) {
			allPoints.addAll(contour.toList());
		}
		Point[] sampledPoints = new Point[numOfPoints];
		int index;
		double samplingIncrement = ( (double) allPoints.size()) / numOfPoints; // number of points between sampled points
		
		for (int i =0; i < numOfPoints; i++) {
			
			index = (int) Math.round(i * samplingIncrement);
			if (index >= allPoints.size()) {
				System.out.println(String.format("Boom, index = %.8f", index));
				index = allPoints.size() - 1;
			}
			sampledPoints[i] = allPoints.get(index);
		}
		
		if ( allPoints.size() < numOfPoints ) {
			System.out.println("Too few points in image, some points sampled multiple times");
		}
		
		return sampledPoints;
	}

	private void drawPoints(Mat matDestination, Point[] points, int pixels) {
		
		if (pixels == 1) {
			
			for (Point point : points) {
				matDestination.put( (int) point.y, (int) point.x, 255);
			}
		}
		else if (pixels == 4) {
	
			Point rectPoint1 = new Point();
			Point rectPoint2 = new Point();
			for (Point point : points) {
				rectPoint1.x = point.x + 1;
				rectPoint1.y = point.y + 1;
				rectPoint2.x = point.x;
				rectPoint2.y = point.y;
				Imgproc.rectangle(matDestination, rectPoint1, rectPoint2, new Scalar (255, 255, 255), Imgproc.FILLED);
			}
		}
		else if (pixels == 9) {
	
			Point rectPoint1 = new Point();
			Point rectPoint2 = new Point();
			for (Point point : points) {
				rectPoint1.x = point.x + 1;
				rectPoint1.y = point.y + 1;
				rectPoint2.x = point.x - 1;
				rectPoint2.y = point.y - 1;
				Imgproc.rectangle(matDestination, rectPoint1, rectPoint2, new Scalar (120, 80, 100), Imgproc.FILLED);
			}
		}
		else 
			throw new IllegalArgumentException("only suported values for point sizes are 1, 4 and 9 px");
	}

	private void drawRegions(Mat dest, Point center, double radius, Point [] points) {
		
		/* draw circles */
		
		Imgproc.circle(dest, center, (int) Math.round(Math.ceil(radius)), new Scalar(55, 55, 55));
		Imgproc.circle(dest, center, (int) Math.round(Math.ceil(radius / 3.0)), new Scalar(55, 55, 55));
		Imgproc.circle(dest, center, (int) Math.round(Math.ceil(( (radius / 3) * 2) )), new Scalar(55, 55, 55));
	
		/* get end points of lines */
		
		float [] data = {0, 30, 60, 90, 120, 150, 180, 210, 240, 240, 270, 300, 330};
		Mat angle = new Mat(1, data.length, CvType.CV_32FC1);
		angle.put(0, 0, data);
		Mat magnitude = new Mat(1, data.length, CvType.CV_32FC1);
		Arrays.fill(data, (float) radius);
		magnitude.put(0, 0, data);
		Mat matPointX = new Mat(1, data.length, CvType.CV_32FC1);
		Mat matPointY = new Mat(1, data.length, CvType.CV_32FC1);
		Core.polarToCart(magnitude, angle, matPointX, matPointY, true);
		
		float [] pointX = new float [matPointX.cols()]; 
		float [] pointY = new float [matPointY.cols()];
		matPointX.get(0, 0, pointX);
		matPointY.get(0, 0, pointY);
		
		/* draw lines */
		
		for (int i = 0; i < pointX.length; i++) {
			
			Point pointXY = new Point(pointX[i] + center.x, pointY[i] + center.y);
			Imgproc.line(dest, center, pointXY, new Scalar(55, 55, 55));
			/*Imgproc.line(dest, center, pointXY, new Scalar(15, 15, 210)); */
		}
		
		/* draw points */
		
		drawPoints(dest, points, 4);
	}

	/**
	 * This method adds histogram to ResultsContainer object
	 * @param result ResultsContainer object to which to add calculated Cpdh histogram
	 */
	private void calculateCpdh(ResultsContainer result) {
		
		float[] magnitude = new float [result.sampledPoints.length];
		float[] angle = new float [result.sampledPoints.length];
		Mat[] polarCoordinates = toPolar (result.sampledPoints, result.center);	
		polarCoordinates[0].get(0, 0, magnitude);
		polarCoordinates[1].get(0, 0, angle);
		
		int[] histogram = new int[3*12]; 		
		int bin = 0;
		
		for (int i = 0; i < angle.length; i++) {
			
			if (angle[i] >= 330.0)
				bin = 0;
			else if (angle[i] >= 300.0)
				bin = 1;
			else if (angle[i] >= 270.0)
				bin = 2;
			else if (angle[i] >= 240.0)
				bin = 3;
			else if (angle[i] >= 210.0)
				bin = 4;
			else if (angle[i] >= 180.0)
				bin = 5;
			else if (angle[i] >= 150.0)
				bin = 6;
			else if (angle[i] >= 120.0)
				bin = 7;
			else if (angle[i] >= 90.0)
				bin = 8;
			else if (angle[i] >= 60.0)
				bin = 9;
			else if (angle[i] >= 30.0)
				bin = 10;
			else 
				bin = 11;
			
			double firstCircle = result.radius / 3.0;
			double secondCircle = firstCircle * 2;
			if (magnitude[i] > secondCircle)
				bin += 24;
			else if (magnitude[i] > firstCircle)
				bin += 12;
			
			histogram[bin]++;
		}
		result.histogram = histogram;
	}

	
	private Mat[] toPolar (Point[] points, Point center) {
		
		Mat pointsX = new Mat(1, points.length, CvType.CV_32FC1);
		Mat pointsY = new Mat(1, points.length, CvType.CV_32FC1);
		
		/* Effectively translate Cartesian system to provided center  */
		
		for (int i = 0; i < points.length; i++) {
			
			pointsX.put(0, i, Math.round(points[i].x - center.x));
			pointsY.put(0, i, Math.round(points[i].y - center.y));
		}
		Mat Magnitude	= new Mat(pointsX.size(), CvType.CV_32FC1);
		Mat Angle		= new Mat(pointsX.size(), CvType.CV_32FC1);	
		
		Core.cartToPolar(pointsX, pointsY, Magnitude, Angle, true);
		Mat[] polarCoordinates = new Mat[2];
		polarCoordinates [0] = Magnitude;
		polarCoordinates [1] = Angle;
		
		return polarCoordinates;
	}

	private boolean checkIfPartOfDataSet(File file) {
		
		String name = file.getName();
		int indexDash = name.lastIndexOf('-');
		String group = name.substring(0, indexDash);
		if (GROUP_NAMES.contains(group))
			return true;
		else
			return false;
	}

	private int readPosition(File file) {
		
		String name = file.getName();
		int indexDash = name.lastIndexOf('-');
		int indexDot = name.lastIndexOf('.');
		return Integer.parseInt(name, indexDash + 1, indexDot, 10) - 1; // -1 because zero indexing
	}

	private double getGroupScore(Mat[] testSet, List<Cpdh> group) {
		
		int capacity = testSet.length * group.size();
		PriorityQueue<Float> scores = new PriorityQueue<Float>(capacity);
		
		boolean emdScoreZero = false; // flag is set when there is perfect match
		
		for (int position = 0; position < group.size(); position++) {
			
			if (this.isPartOfDataSet && this.position == position)
				continue;
			
			for (int variation = 0; variation < testSet.length; variation++) {
				
				Float emd = Imgproc.EMD(testSet[variation], group.get(position).signature, Imgproc.DIST_USER, COST_MATRIX);
				scores.add(emd);
				if (emd == 0.0f)
					emdScoreZero = true;
			}
		}
		int take = 1;
		double score = 0.00;
		while (take > 0) {
			System.out.println(scores.peek() );
			score += scores.remove();
			take--;
		}
		
		if (emdScoreZero)
			System.out.println("\nWarning: EMD score = 0.0\n");
			
		return score;
	}

	private Mat[] getSignatureVariations() {
		
		int[][] histogram2d = to2D(histogram);
		
		ArrayList<int[][]> variations = new ArrayList<int[][]>(24);
		int[][] mirrored = getMirored(histogram2d);
		variations.addAll(getRotations(histogram2d));
		variations.addAll(getRotations(mirrored));		
		
		/* Conversion to signature */
		Mat[] signatures = new Mat[variations.size()];
		for (int i = 0; i < variations.size(); i++) {
			
			signatures[i] = toSignature( to1D(variations.get(i)));
		}
		return signatures;
	}

	private int[][] getMirored(int[][] histogram2d) {
		
		int[][] mirrored = new int[histogram2d.length][];
		
		int lastIndex = histogram2d.length - 1;
		for (int i = 0; i < mirrored.length; i++) {
			
			mirrored[i] = histogram2d[lastIndex - i];
		}
		
		return mirrored;
	}

	private ArrayList<int[][]> getRotations(int[][] histogram2d) {
		
		int[] [][] rotations = new int[12] [histogram2d.length][];
		int cols = histogram2d.length;
		for (int col = 0; col < cols; col++) {			// each column ...
			for (int i = 0; i < rotations.length; i++) {
				
				rotations[i] [(col + i) % cols] = histogram2d[col]; 		// ... is shifted for i spaces.
			}
		}
		return new ArrayList<int[][]>(Arrays.asList(rotations));
	}

	private Mat toSignature(int[] histogram) {
		
		Mat signature = new Mat(histogram.length, 1, CvType.CV_32SC1);
		signature.put(0, 0, histogram);
		signature.convertTo(signature, CvType.CV_32FC1);
		return signature;
	}

	private int[] to1D(int[][] histogram2D) {
		
		int[] histogram = new int[36];
		
		/* loop through source */
		int cols = 12;
		int rows = 3;
		for (int col = 0; col < cols; col++) {
			for (int row = 0; row < rows; row++) {
			
				histogram [row * 12 + col] = histogram2D[col][row];
			}
		}
		return histogram;
	}

	/* This 2D configuration enables easy manipulation */
	private int[][] to2D(int[] histogram) {
		
		int cols = 12;
		int rows = 3;
		int[][] histogram2D = new int[cols][rows];
		
		/* loop through destination */
		for (int col = 0; col < cols; col++) {
			for (int row = 0; row < rows; row++) {
			
				histogram2D[col][row] = histogram [row * 12 + col];
			}
		}
		return histogram2D;
	}

	String retrieveShape(List<List<Cpdh>> dataSet) {
		
		int groupMatched = 0;
		double score = Double.MAX_VALUE , newScore = Double.MAX_VALUE;
		Mat[] testSet = this.getSignatureVariations();
		
		/* for each group */
		for (int i = 0; i < dataSet.size(); i++) {
			
			newScore = getGroupScore(testSet, dataSet.get(i));
			if (newScore < score) {
				
				score = newScore;
				groupMatched = i;
			}
		}
		return GROUP_NAMES.get(groupMatched);
	}

	List<Image> getImages() {
		return images;
	}

	File getFile() {
		return file;
	}
	
	String toString1Line() {
		
		StringBuilder output = new StringBuilder(75);
		for(int bin : histogram) {
			output.append(bin).append(' ');
		}
		return output.toString();
	}

	public String toString() {
		
		StringBuilder sb = new StringBuilder("[");
		int col = 0;
		for (int i = 0; i < histogram.length; i++ ) {
			
			sb.append(String.format(" %2d", histogram[i]));
			if (col >= 11) {
				sb.append(";\n ");
				col = 0;
				continue;
			}
			sb.append(", ");
			col++;
		}
		sb.setLength(sb.length() - 3);
		sb.append(" ]");
		
		return sb.toString();
	}


	private class ResultsContainer {
	
		private int[] histogram;
		private float radius;
		private Point center;
		private Point[] sampledPoints;
		private ArrayList<Mat> matImages = new ArrayList<Mat>();
	
		private List<Image> getImages() {
			
			/*
			 * make FX Image
			 * Mat -> BufferedImage -> FX Image
			 * more details at:
			 * https://stackoverflow.com/questions/26515981/display-image-using-mat-in-opencv-java
			 * and:
			 * http://lovelace.augustana.edu/q2a/index.php/217/which-way-of-converting-mat-to-javafx-image-is-fastest
			 */			
			List<Image> imagesFX = new ArrayList<Image>();
	
			var itr = matImages.iterator();
	
			while(itr.hasNext()) {
	
				Mat mat = itr.next();
	
				int type;
				if	(mat.channels() == 1)
					type = BufferedImage.TYPE_BYTE_GRAY;
				else if (mat.channels() == 3)
					type = BufferedImage.TYPE_3BYTE_BGR;
				else 
					throw new IllegalArgumentException("Unsuported number of channels");
	
				BufferedImage image = new BufferedImage(mat.width(), mat.height(), type);
				DataBufferByte dataBuffer = (DataBufferByte) image.getRaster().getDataBuffer();
				byte[] data = dataBuffer.getData();
				mat.get(0, 0, data);
				imagesFX.add(SwingFXUtils.toFXImage(image, null));
			}
			return List.copyOf(imagesFX); // returns immutable list
		}
	}
}
