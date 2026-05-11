package DBMS;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

public class DBApp
{
	static int dataPageSize = 2;

	// ── MS1 methods ───────────────────────────────────────────────────────────

	public static void createTable(String tableName, String[] columnsNames)
	{
		Table t = new Table(tableName, columnsNames);
		FileManager.storeTable(tableName, t);
	}

	public static void insert(String tableName, String[] record)
	{
		Table t = FileManager.loadTable(tableName);
		t.insert(record);
		// Update every bitmap index that already exists for this table
		for (String colName : t.getIndexedColumns())
		{
			BitmapIndex idx = FileManager.loadTableIndex(tableName, colName);
			if (idx != null)
			{
				idx.addRecord(record[t.getColumnIndex(colName)]);
				FileManager.storeTableIndex(tableName, colName, idx);
			}
		}
		FileManager.storeTable(tableName, t);
	}

	public static ArrayList<String[]> select(String tableName)
	{
		Table t = FileManager.loadTable(tableName);
		ArrayList<String[]> res = t.select();
		FileManager.storeTable(tableName, t);
		return res;
	}

	public static ArrayList<String[]> select(String tableName, int pageNumber, int recordNumber)
	{
		Table t = FileManager.loadTable(tableName);
		ArrayList<String[]> res = t.select(pageNumber, recordNumber);
		FileManager.storeTable(tableName, t);
		return res;
	}

	public static ArrayList<String[]> select(String tableName, String[] cols, String[] vals)
	{
		Table t = FileManager.loadTable(tableName);
		ArrayList<String[]> res = t.select(cols, vals);
		FileManager.storeTable(tableName, t);
		return res;
	}

	public static String getFullTrace(String tableName)
	{
		Table t = FileManager.loadTable(tableName);
		return t.getFullTrace();
	}

	public static String getLastTrace(String tableName)
	{
		Table t = FileManager.loadTable(tableName);
		return t.getLastTrace();
	}

	// ── MS2: bitmap index ─────────────────────────────────────────────────────

	public static void createBitMapIndex(String tableName, String colName)
	{
		long start = System.currentTimeMillis();
		Table t = FileManager.loadTable(tableName);
		int colIdx = t.getColumnIndex(colName);
		BitmapIndex idx = new BitmapIndex();
		for (int i = 0; i < t.getPageCount(); i++)
		{
			Page p = FileManager.loadTablePage(tableName, i);
			if (p != null)
				for (String[] rec : p.select())
					idx.addRecord(rec[colIdx]);
		}
		FileManager.storeTableIndex(tableName, colName, idx);
		t.addIndexedColumn(colName);
		long elapsed = System.currentTimeMillis() - start;
		t.addTrace("Index created for column: " + colName + ", execution time (mil):" + elapsed);
		FileManager.storeTable(tableName, t);
	}

	public static String getValueBits(String tableName, String colName, String value)
	{
		BitmapIndex idx = FileManager.loadTableIndex(tableName, colName);
		return idx.getBits(value);
	}

	// ── MS2: selectIndex ──────────────────────────────────────────────────────

	public static ArrayList<String[]> selectIndex(String tableName, String[] cols, String[] vals)
	{
		long start = System.currentTimeMillis();
		Table t = FileManager.loadTable(tableName);

		// Split condition columns into indexed / non-indexed (preserving condition order)
		ArrayList<String> indexed    = new ArrayList<String>();
		ArrayList<String> nonIndexed = new ArrayList<String>();
		for (String col : cols)
		{
			if (t.getIndexedColumns().contains(col)) indexed.add(col);
			else                                      nonIndexed.add(col);
		}

		ArrayList<String[]> result;
		int indexedSelCount = 0;

		if (indexed.isEmpty())
		{
			// No index available — full linear scan (reuse Table.select internals)
			String[] cond = t.fixCond(cols, vals);
			result = new ArrayList<String[]>();
			for (int i = 0; i < t.getPageCount(); i++)
			{
				Page p = FileManager.loadTablePage(tableName, i);
				if (p != null) result.addAll(p.select(cond));
			}
		}
		else
		{
			// AND the bit strings for every indexed condition column
			String andBits = null;
			for (String col : indexed)
			{
				int condPos = indexOf(cols, col);
				BitmapIndex idx = FileManager.loadTableIndex(tableName, col);
				String bits = idx.getBits(vals[condPos]);
				andBits = (andBits == null) ? bits : andBitStrings(andBits, bits);
			}

			// Collect candidate records from positions where bit == '1'
			ArrayList<String[]> candidates = new ArrayList<String[]>();
			for (int pos = 0; pos < andBits.length(); pos++)
			{
				if (andBits.charAt(pos) == '1')
				{
					int pageNum = pos / dataPageSize;
					int recNum  = pos % dataPageSize;
					Page p = FileManager.loadTablePage(tableName, pageNum);
					if (p != null)
					{
						ArrayList<String[]> pr = p.select(recNum);
						if (!pr.isEmpty()) candidates.add(pr.get(0));
					}
				}
			}
			indexedSelCount = candidates.size();

			// Further filter candidates by non-indexed conditions
			if (nonIndexed.isEmpty())
			{
				result = candidates;
			}
			else
			{
				String[] nonIdxCols = nonIndexed.toArray(new String[0]);
				String[] nonIdxVals = new String[nonIndexed.size()];
				for (int i = 0; i < nonIndexed.size(); i++)
					nonIdxVals[i] = vals[indexOf(cols, nonIndexed.get(i))];
				String[] cond = t.fixCond(nonIdxCols, nonIdxVals);
				result = new ArrayList<String[]>();
				for (String[] rec : candidates)
					if (matchesCond(rec, cond)) result.add(rec);
			}
		}

		long elapsed = System.currentTimeMillis() - start;

		// Build trace entry matching spec format
		StringBuilder tracer = new StringBuilder();
		tracer.append("Select index condition:")
		      .append(Arrays.toString(cols)).append("->").append(Arrays.toString(vals));
		if (!indexed.isEmpty())
		{
			tracer.append(", Indexed columns: ").append(indexed);
			tracer.append(", Indexed selection count: ").append(indexedSelCount);
		}
		if (!nonIndexed.isEmpty())
			tracer.append(", Non Indexed: ").append(nonIndexed);
		tracer.append(", Final count: ").append(result.size());
		tracer.append(", execution time (mil):").append(elapsed);

		t.addTrace(tracer.toString());
		FileManager.storeTable(tableName, t);
		return result;
	}

	// ── MS2: validateRecords ─────────────────────────────────────────────────

	public static ArrayList<String[]> validateRecords(String tableName)
	{
		Table t = FileManager.loadTable(tableName);
		int pageCount    = t.getPageCount();
		int recordsCount = t.getRecordsCount();
		ArrayList<String[]> missing = new ArrayList<String[]>();

		for (int i = 0; i < pageCount; i++)
		{
			Page p = FileManager.loadTablePage(tableName, i);
			if (p == null)
			{
				int cnt = (i < pageCount - 1) ? dataPageSize
						: (recordsCount % dataPageSize == 0 ? dataPageSize
								: recordsCount % dataPageSize);
				for (int j = 0; j < cnt; j++)
					missing.add(new String[0]);
			}
		}

		t.addTrace("Validating records: " + missing.size() + " records missing.");
		FileManager.storeTable(tableName, t);
		return missing;
	}

	// ── MS2: recoverRecords ──────────────────────────────────────────────────

	public static void recoverRecords(String tableName, ArrayList<String[]> missing)
	{
		Table t = FileManager.loadTable(tableName);
		int pageCount    = t.getPageCount();
		int recordsCount = t.getRecordsCount();
		ArrayList<Integer> recoveredPages = new ArrayList<Integer>();
		int missingIdx = 0;

		for (int i = 0; i < pageCount && missingIdx < missing.size(); i++)
		{
			Page p = FileManager.loadTablePage(tableName, i);
			if (p == null)
			{
				int cnt = (i < pageCount - 1) ? dataPageSize
						: (recordsCount % dataPageSize == 0 ? dataPageSize
								: recordsCount % dataPageSize);
				Page newPage = new Page();
				for (int j = 0; j < cnt && missingIdx < missing.size(); j++, missingIdx++)
					newPage.insert(missing.get(missingIdx));
				FileManager.storeTablePage(tableName, i, newPage);
				recoveredPages.add(i);
			}
		}

		t.addTrace("Recovering " + missing.size() + " records in pages: " + recoveredPages + ".");
		FileManager.storeTable(tableName, t);
	}

	// ── private helpers ───────────────────────────────────────────────────────

	private static int indexOf(String[] arr, String key)
	{
		for (int i = 0; i < arr.length; i++)
			if (arr[i].equals(key)) return i;
		return -1;
	}

	private static String andBitStrings(String a, String b)
	{
		int len = Math.min(a.length(), b.length());
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < len; i++)
			sb.append((a.charAt(i) == '1' && b.charAt(i) == '1') ? '1' : '0');
		return sb.toString();
	}

	private static boolean matchesCond(String[] record, String[] cond)
	{
		for (int i = 0; i < cond.length; i++)
			if (cond[i] != null && !cond[i].equals(record[i])) return false;
		return true;
	}

	// ── main (example from spec) ──────────────────────────────────────────────

	public static void main(String[] args) throws IOException
	{
		FileManager.reset();
		String[] cols = {"id", "name", "major", "semester", "gpa"};
		createTable("student", cols);
		String[] r1 = {"1", "stud1", "CS", "5", "0.9"};
		insert("student", r1);
		String[] r2 = {"2", "stud2", "BI", "7", "1.2"};
		insert("student", r2);
		String[] r3 = {"3", "stud3", "CS", "2", "2.4"};
		insert("student", r3);

		createBitMapIndex("student", "gpa");
		createBitMapIndex("student", "major");

		System.out.println("Bitmap of the value of CS from the major index: "
				+ getValueBits("student", "major", "CS"));
		System.out.println("Bitmap of the value of 1.2 from the gpa index: "
				+ getValueBits("student", "gpa", "1.2"));

		String[] r4 = {"4", "stud4", "CS", "9", "1.2"};
		insert("student", r4);
		String[] r5 = {"5", "stud5", "BI", "4", "3.5"};
		insert("student", r5);

		System.out.println("After new insertions:");
		System.out.println("Bitmap of the value of CS from the major index: "
				+ getValueBits("student", "major", "CS"));
		System.out.println("Bitmap of the value of 1.2 from the gpa index: "
				+ getValueBits("student", "gpa", "1.2"));

		System.out.println("Output of selection using index when all columns of the select conditions are indexed:");
		ArrayList<String[]> result1 = selectIndex("student", new String[]{"major", "gpa"}, new String[]{"CS", "1.2"});
		for (String[] array : result1) { for (String str : array) System.out.print(str + " "); System.out.println(); }
		System.out.println("Last trace of the table: " + getLastTrace("student"));
		System.out.println("--------------------------------");

		System.out.println("Output of selection using index when only one column of the columns of the select conditions are indexed:");
		ArrayList<String[]> result2 = selectIndex("student", new String[]{"major", "semester"}, new String[]{"CS", "5"});
		for (String[] array : result2) { for (String str : array) System.out.print(str + " "); System.out.println(); }
		System.out.println("Last trace of the table: " + getLastTrace("student"));
		System.out.println("--------------------------------");

		System.out.println("Output of selection using index when some of the columns of the select conditions are indexed:");
		ArrayList<String[]> result3 = selectIndex("student", new String[]{"major", "semester", "gpa"}, new String[]{"CS", "5", "0.9"});
		for (String[] array : result3) { for (String str : array) System.out.print(str + " "); System.out.println(); }
		System.out.println("Last trace of the table: " + getLastTrace("student"));
		System.out.println("--------------------------------");

		System.out.println("Full Trace of the table:");
		System.out.println(getFullTrace("student"));
		System.out.println("--------------------------------");
		System.out.println("The trace of the Tables Folder:");
		System.out.println(FileManager.trace());
	}
}
