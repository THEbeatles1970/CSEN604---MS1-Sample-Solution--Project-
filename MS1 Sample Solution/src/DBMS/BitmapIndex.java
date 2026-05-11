package DBMS;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;

public class BitmapIndex implements Serializable
{
	private HashMap<String, StringBuilder> bitMap;
	private int totalRecords;

	public BitmapIndex()
	{
		bitMap = new HashMap<>();
		totalRecords = 0;
	}

	public void addRecord(String value)
	{
		// Extend all existing bit strings by one '0'
		for (StringBuilder sb : bitMap.values())
			sb.append('0');

		if (!bitMap.containsKey(value))
		{
			// New value: fill prior positions with '0', then '1'
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < totalRecords; i++)
				sb.append('0');
			sb.append('1');
			bitMap.put(value, sb);
		}
		else
		{
			// Flip the last '0' (just appended) to '1'
			StringBuilder sb = bitMap.get(value);
			sb.setCharAt(sb.length() - 1, '1');
		}
		totalRecords++;
	}

	public String getBits(String value)
	{
		if (bitMap.containsKey(value))
			return bitMap.get(value).toString();
		// Value not present: return all zeros
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < totalRecords; i++)
			sb.append('0');
		return sb.toString();
	}

	public ArrayList<Integer> getMatchingPositions(String value)
	{
		String bits = getBits(value);
		ArrayList<Integer> positions = new ArrayList<>();
		for (int i = 0; i < bits.length(); i++)
			if (bits.charAt(i) == '1')
				positions.add(i);
		return positions;
	}

	public int getTotalRecords()
	{
		return totalRecords;
	}
}
