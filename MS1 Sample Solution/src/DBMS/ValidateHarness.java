package DBMS;

import java.io.File;

public class ValidateHarness
{
	public static void main(String[] args) throws Exception
	{
		FileManager.reset();
		DBApp.dataPageSize = 3;
		String[] cols = {"a","b","c","d","e","f","g"};
		DBApp.createTable("n5l", cols);
		for (int i = 0; i < 199; i++)
		{
			String[] record = new String[cols.length];
			record[0] = cols[0] + i;
			for (int j = 1; j < cols.length; j++)
				record[j] = cols[j] + (i % (j + 1));
			DBApp.insert("n5l", record);
		}
		int[] toDelete = {0, 1, 65};
		for (int page : toDelete)
		{
			File file = new File(FileManager.directory.getAbsolutePath()
					+ File.separator + "n5l" + File.separator + page + ".db");
			System.out.println(page + " delete=" + file.delete() + " exists=" + file.exists());
		}
		DBApp.validateRecords("n5l");
		System.out.println(DBApp.getLastTrace("n5l"));
	}
}
