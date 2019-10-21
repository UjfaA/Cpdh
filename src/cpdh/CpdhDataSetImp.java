package cpdh;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;


public class CpdhDataSetImp implements CpdhDataSet {
	
	private final int numOfPoints;
	private final Map<String, Set<Cpdh>> groups;

	public CpdhDataSetImp(int numOfPoints) {
		this.numOfPoints = numOfPoints;
		this.groups = new HashMap<String, Set<Cpdh>>(20);
	}
	
	@Override
	public boolean contains(Cpdh cpdh) {
		
		if (isEmpty()) return false;
		boolean isInTheDataset = false;
		for (Set<Cpdh> group : groups.values()) {
			if (group.contains(cpdh))
				isInTheDataset = true;
		}
		return isInTheDataset;
	}
	
	@Override
	public boolean containsCpdhInGroup(Cpdh cpdh, String groupName) {
		Set<Cpdh> group = groups.get(groupName);
		return group.contains(cpdh);
	}

	/**
	 * @throws IllegalArgumentException if cpdh == null
	 * or when numOfPoints attributes of cpdh and data set do not match.
	 */
	@Override			
	public void put(Cpdh cpdh) {
		
		throwExceptionIfnotValid(cpdh);
		String group = groupFrom(cpdh.getFilename());
		groups.putIfAbsent(group, new HashSet<Cpdh>());
		groups.get(group).add(cpdh);
	}

	private void throwExceptionIfnotValid(Cpdh cpdh) {
		
		if (cpdh == null) {
			throw new IllegalArgumentException("cpdh can't be null");
		}
		if (cpdh.getNumOfPoints() != numOfPoints) {
			throw new IllegalArgumentException(
					String.format("Number of points of the Cpdh for \"%s\" does not match to that of the data set (%d != %d)"
								,cpdh.getFilename(), cpdh.getNumOfPoints(), numOfPoints) );
		}
	}

	private String groupFrom(String filename) {
		int end = filename.indexOf('-');
		return filename.substring(0, end);
	}

	@Override
	public GroupScore matchCpdhToGroup(Cpdh cpdh) {
		
		List<GroupScore> groupScores = new ArrayList<GroupScore>();
		groups.forEach((group, cpdhsInTheGroup) -> {
			double score = determineGroupScore(cpdh, cpdhsInTheGroup);
			groupScores.add(new GroupScore(group, score));
		});
		return Collections.min(groupScores);
	}

	private double determineGroupScore(Cpdh testCpdh, Set<Cpdh> cpdhSet) {
		 
		var bestEmdScores = new PriorityQueue<Float>();
		
		cpdhSet.forEach(cpdhInSet -> {
			bestEmdScores.add(Cpdh.emd(testCpdh, cpdhInSet));
		});
		return bestEmdScores.remove();
	}

	@Override
	public void loadFrom(File file) throws IOException {
		
		if ( ! file.isFile()) throw new FileNotFoundException("Can't find file: " +file.getName());
		if ( ! file.canRead()) throw new IOException("Can't read from file: " + file.getName());
		
		this.groups.clear();
		
		ListIterator<String> line = Files.readAllLines(file.toPath()).listIterator();
		while (line.hasNext()) {
			String filename = line.next();
			String data		= line.next();
			try {
				this.put(new Cpdh(filename, data));
			} catch (NumberFormatException e) {
				this.groups.clear();
				String message = "Data in file: " + file.getName() + " is corupted. " + e.getMessage();
				throw new IOException(message);
			}
		}
	}

	@Override
	public void writeTo(File file) {
		try {
			Files.writeString(file.toPath(), dataToLines(), 
					StandardOpenOption.CREATE,
					StandardOpenOption.TRUNCATE_EXISTING,
					StandardOpenOption.WRITE);
		} catch (Exception e) {
			System.out.println("Error writing to file: " + file.getName());
			e.printStackTrace();
		}
	}
	
	private StringBuilder dataToLines() {
		
		StringBuilder lines = new StringBuilder(131072); //2pow(17) initial capacity
		groups.forEach((group, cpdhSet) -> {
			cpdhSet.forEach(cpdh ->
				lines.append(cpdh.getFilename() + '\n')
				.append(cpdh.toString1Line() + '\n'));
		});
		lines.deleteCharAt(lines.length() - 1);
		return lines;
	}
	
	@Override
	public boolean isEmpty() {
		return groups.isEmpty();
	}
	
}
