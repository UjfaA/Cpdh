package cpdh;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;



public class Cpdh {
	
	private static final List<String> GROUP_NAMES = getGroupNames(); // List of names of groups from MPEG-7 shape data set.
	
//	private final int numOfPoints;
	private final int[] histogram;
	private final Mat signature;
	private final List<Image> images;
	private boolean isPartOfDataSet; // Infer from file name.  
	private int position; // Position in group in data set. Derived from file name. If not part of data set then = -1;

	private static List<String> getGroupNames() { 

		return List.of("apple", "bat", "beetle", "bell", "bird", "Bone", "bottle", "brick", "butterfly",
				"camel", "car", "carriage", "catle", "cellular_phone", "chicken", "children", "chopper",
				"classic", "Comma", "crown", "cup", "deer", "device0", "device1", "device2", "device3",
				"device4", "device5", "device6", "device7", "device8", "device9", "dog", "elephant", "face",
				"fish", "flatfish", "fly", "fork", "fountain", "frog", "Glas", "guitar", "hammer", "hat",
				"HCircle", "Heart", "horse", "horseshoe", "jar", "key", "lizzard", "lmfish", "Misk",
				"octopus", "pencil", "personal_car", "pocket", "rat", "ray", "sea_snake", "shoe", "spoon",
				"spring", "stef", "teddy", "tree", "truck", "turtle", "watch");
	}
	
	public Cpdh(File file, int numOfPoints) {

		this(file, numOfPoints, true);
	}

	public Cpdh(File file, int numOfPoints, boolean saveImages) {
		
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
		
		System.out.println(isPartOfDataSet);
		System.out.println(position);
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
		Imgproc.threshold(matGray, matBinary, 128, 255, Imgproc.THRESH_BINARY);
		
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
		
		/* black = 0 ; white = -1 */
		
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
		Point[] sampledPoints = new Point [numOfPoints];
		int index;
		double samplingIncrement = ( (double) allPoints.size()) / numOfPoints; // number of points between sampled points
		
		for (int i =0; i < numOfPoints; i++) {
			
			index = (int) Math.round(i * samplingIncrement);
			if (index >= allPoints.size()) {
				System.out.println(String.format("Boom, index = %.8f", index));
				index = allPoints.size() - 1;
			}
			sampledPoints [i] = allPoints.get(index);
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
	 * 
	 * @param result This method adds histogram to result object
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

	
	private Mat toSignature(int[] histogram) {
		
		Mat signature = new Mat(histogram.length, 1, CvType.CV_32SC1);
		signature.put(0, 0, histogram);
		signature.convertTo(signature, CvType.CV_32FC1);
		return signature;
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

	List<Image> getImages() {
		return images;
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
			
			sb.append(
					String.format(" %2d", histogram[i]));
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
