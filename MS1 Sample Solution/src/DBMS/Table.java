package DBMS;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;

public class Table implements Serializable
{
	private String name;
	private String[] columnsNames;
	private int pageCount;
	private int recordsCount;
	private ArrayList<String> trace;
	private ArrayList<String> indexedColumns;

	public Table(String name, String[] columnsNames)
	{
		super();
		this.name = name;
		this.columnsNames = columnsNames;
		this.trace = new ArrayList<String>();
		this.indexedColumns = new ArrayList<String>();
		this.trace.add("Table created name:" + name + ", columnsNames:"
				+ Arrays.toString(columnsNames));
	}

	@Override
	public String toString()
	{
		return "Table [name=" + name + ", columnsNames="
				+ Arrays.toString(columnsNames) + ", pageCount=" + pageCount
				+ ", recordsCount=" + recordsCount + "]";
	}

	// ── accessors ─────────────────────────────────────────────────────────────

	public int getPageCount()    { return pageCount; }
	public int getRecordsCount() { return recordsCount; }

	public ArrayList<String> getIndexedColumns() { return indexedColumns; }
	public void addIndexedColumn(String col)      { indexedColumns.add(col); }

	public int getColumnIndex(String colName)
	{
		for (int i = 0; i < columnsNames.length; i++)
			if (columnsNames[i].equals(colName)) return i;
		return -1;
	}

	public void addTrace(String entry) { trace.add(entry); }

	// ── insert ────────────────────────────────────────────────────────────────

	public void insert(String[] record)
	{
		long startTime = System.currentTimeMillis();
		Page current = FileManager.loadTablePage(this.name, pageCount - 1);
		if (current == null || !current.insert(record))
		{
			current = new Page();
			current.insert(record);
			pageCount++;
		}
		FileManager.storeTablePage(this.name, pageCount - 1, current);
		recordsCount++;
		long stopTime = System.currentTimeMillis();
		this.trace.add("Inserted:" + Arrays.toString(record) + ", at page number:" + (pageCount - 1)
				+ ", execution time (mil):" + (stopTime - startTime));
	}

	// ── select helpers ────────────────────────────────────────────────────────

	public String[] fixCond(String[] cols, String[] vals)
	{
		String[] res = new String[columnsNames.length];
		for (int i = 0; i < res.length; i++)
			for (int j = 0; j < cols.length; j++)
				if (columnsNames[i].equals(cols[j]))
					res[i] = vals[j];
		return res;
	}

	public ArrayList<String[]> select(String[] cols, String[] vals)
	{
		String[] cond = fixCond(cols, vals);
		String tracer = "Select condition:" + Arrays.toString(cols) + "->" + Arrays.toString(vals);
		ArrayList<ArrayList<Integer>> pagesResCount = new ArrayList<ArrayList<Integer>>();
		ArrayList<String[]> res = new ArrayList<String[]>();
		long startTime = System.currentTimeMillis();
		for (int i = 0; i < pageCount; i++)
		{
			Page p = FileManager.loadTablePage(this.name, i);
			if (p == null) continue;
			ArrayList<String[]> pRes = p.select(cond);
			if (pRes.size() > 0)
			{
				ArrayList<Integer> pr = new ArrayList<Integer>();
				pr.add(i);
				pr.add(pRes.size());
				pagesResCount.add(pr);
				res.addAll(pRes);
			}
		}
		long stopTime = System.currentTimeMillis();
		tracer += ", Records per page:" + pagesResCount + ", records:" + res.size()
				+ ", execution time (mil):" + (stopTime - startTime);
		this.trace.add(tracer);
		return res;
	}

	public ArrayList<String[]> select(int pageNumber, int recordNumber)
	{
		String tracer = "Select pointer page:" + pageNumber + ", record:" + recordNumber;
		ArrayList<String[]> res = new ArrayList<String[]>();
		long startTime = System.currentTimeMillis();
		Page p = FileManager.loadTablePage(this.name, pageNumber);
		if (p != null)
		{
			ArrayList<String[]> pRes = p.select(recordNumber);
			if (pRes.size() > 0)
				res.addAll(pRes);
		}
		long stopTime = System.currentTimeMillis();
		tracer += ", total output count:" + res.size()
				+ ", execution time (mil):" + (stopTime - startTime);
		this.trace.add(tracer);
		return res;
	}

	public ArrayList<String[]> select()
	{
		ArrayList<String[]> res = new ArrayList<String[]>();
		long startTime = System.currentTimeMillis();
		for (int i = 0; i < pageCount; i++)
		{
			Page p = FileManager.loadTablePage(this.name, i);
			if (p != null) res.addAll(p.select());
		}
		long stopTime = System.currentTimeMillis();
		this.trace.add("Select all pages:" + pageCount + ", records:" + recordsCount
				+ ", execution time (mil):" + (stopTime - startTime));
		return res;
	}

	// ── trace ─────────────────────────────────────────────────────────────────

	public String getFullTrace()
	{
		String res = "";
		for (int i = 0; i < this.trace.size(); i++)
			res += this.trace.get(i) + "\n";
		return res + "Pages Count: " + pageCount + ", Records Count: " + recordsCount
				+ ", Indexed Columns: " + indexedColumns;
	}

	public String getLastTrace()
	{
		return this.trace.get(this.trace.size() - 1);
	}
}
