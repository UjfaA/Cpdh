package cpdh;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

import javafx.concurrent.Task;

/**
 * This Class contains method that prepares and returns javafx.concurrent.Task objects.  
 * @author Aleksandar
 */
public class Tasks {

	/**
	 * prepares javafx Task that constructs Cpdh data sets with 50, 100 and 250 points.
	 * Each of individual sets is constructed asynchronously and placed in a Map< Integer, List<List<Cpdh>> >  
	 * MPEG7- shape image date set is assumed.
	 * @param dir directory where MPEG7- shape image date set is located
	 * @return configured javafx Task that constructs Cpdh data set
	 */
	static Task< Map<Integer, List<List<Cpdh>>> > newConstructDataSetsTask(File dir) {
		
		return new Task< Map<Integer, List<List<Cpdh>>> >() {
			
			@Override
			protected Map<Integer, List<List<Cpdh>> > call() throws Exception {
				
				updateMessage("Constructing data sets ...");
				Map<Integer, List<List<Cpdh>> > dataSets = new HashMap<>();
				File[] files = dir.listFiles((directory, name) -> {
					return name.endsWith(".jpg") || name.endsWith("jpeg") || name.endsWith(".png");
				});
				if (files.length != 1400) {
					updateMessage("Please make sure there are only images from data set in folder: " + dir.getName());
					cancel();
				}
				// file.listFiles() do not guarantee any order 
				sortFiles(files);
				
				/* data set constructor is wrapped in FutureTask and started right away. */
				Thread t;
				var dataSetConstructor50 = new ConstructSingleDataSet(files, 50);
				FutureTask< List<List<Cpdh>> > dataset50Task = new FutureTask<>(dataSetConstructor50);
				t = new Thread(dataset50Task);
				t.setDaemon(true);
				t.start();
				var dataSetConstructor100 = new ConstructSingleDataSet(files, 100);
				FutureTask< List<List<Cpdh>> > dataset100Task = new FutureTask<>(dataSetConstructor100);
				t = new Thread(dataset100Task);
				t.setDaemon(true);
				t.start();
				var dataSetConstructor250 = new ConstructSingleDataSet(files, 250);
				FutureTask< List<List<Cpdh>> > dataset250Task = new FutureTask<>(dataSetConstructor250);
				t = new Thread(dataset250Task);
				t.setDaemon(true);
				t.start();
				
				while (!dataset250Task.isDone() || !dataset100Task.isDone() || !dataset50Task.isDone()) {
					
					if (isCancelled() || Thread.interrupted()) {
						updateMessage("Constructing data sets was canceled.");
						dataset50Task.cancel(true);
						dataset100Task.cancel(true);
						dataset250Task.cancel(true);
						cancel();
						}
					int workDone =  dataSetConstructor250.workDone + dataSetConstructor100.workDone + dataSetConstructor50.workDone;
					updateProgress(workDone, 70*3);
					Thread.sleep(250);
				}
				updateProgress(70*3, 70*3);
				
				// blocks until all data sets are created
				dataSets.put(50, dataset50Task.get());
				dataSets.put(100, dataset100Task.get());
				dataSets.put(250, dataset250Task.get());
				
				return dataSets;
			}
		};
	}

	private static void sortFiles(File[] files) {

		Arrays.sort(files, new Comparator<File>() {

			@Override
			public int compare(File f1, File f2) {

				String name1 = f1.getName();
				int indexDash = name1.lastIndexOf('-');
				int indexDot = name1.lastIndexOf('.');
				String group1 = name1.substring(0, indexDash);
				int  position1 = Integer.parseInt(name1, indexDash + 1, indexDot, 10);

				String name2 = f2.getName();
				indexDash = name2.lastIndexOf('-');
				indexDot = name2.lastIndexOf('.');
				String group2 = name2.substring(0, indexDash);
				int  position2 = Integer.parseInt(name2, indexDash + 1, indexDot, 10);

				int groupComp = group1.compareToIgnoreCase(group2);
				if (groupComp == 0)
					return Integer.compare(position1, position2);
				else
					return groupComp;
			}
		});
	}

	static class ConstructSingleDataSet implements Callable< List<List<Cpdh>> > {
		
		private final int groups = 70;
		private final int positions = 20;
		private final File[] files;
		private final int numOfPoints;
		private int workDone;
		
		public ConstructSingleDataSet(File[] files, int numOfPoints) {
			this.files			= files;
			this.numOfPoints	= numOfPoints;
		}
		
		@Override
		public List<List<Cpdh>> call() {
			
			List<List<Cpdh>> dataSet = new ArrayList<>();
			
			for(int group = 0; group < groups; group++) {
				if (Thread.interrupted() != true) { 
					/* Load each group */
					List<Cpdh> cpdhGroup = new ArrayList<Cpdh>(positions);
					for(int position = 0; position < positions; position++) {
						int fileIndex = group * positions + position;
						cpdhGroup.add(new Cpdh(files[fileIndex], numOfPoints, false));
					}
					dataSet.add(cpdhGroup);
					workDone = group + 1;
				}
				else return null;
			}
			return dataSet;
		}
	}
}
