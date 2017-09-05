<p><span style="font-size: 6px;"><strong>What is SQLiteLib?</strong></span><br />
<span style="font-size: 4px;">Many users on spigot have used my tutorial <a href="'https://www.spigotmc.org/threads/how-to-sqlite.56847/'">How to: SQLite</a>. I noticed I had a ton of users adding me on Discord looking for help. They found the code too difficult to use or simply didnt understand how it worked. This library is aimed to fix this. SQLiteLib gives the basic programmer all the tools they need to:</span><br />
<ul><li><span style="font-size: 4px;">Create Databases</span><br />
<li><span style="font-size: 4px;">Query Single Values</span><br />
<li><span style="font-size: 4px;">Query List of values</span><br />
<li><span style="font-size: 4px;">Query Map of lists</span><br />
<li>Execute SQL statements</ul><br />
SQLiteLib can be used in game too!</p>

<p><span style="font-size: 6px;"><strong>Commands:</strong></span><br />
<span style="font-size: 4px;">/sqlite or /sl - base command<br />
/sl execute {database} {statement} - Execute a statement on database<br />
/sl init {database} {initial_statement} - Create/Initiate database<br />
/sl queryvalue {database} {row} {statement} - Print the queried value.<br />
/sl queryRow {database} {row} {statement} - Print all queried values.<br />
</span><br />
<span style="font-size: 6px;"><strong>Permissions:</strong></span><br />
<span style="font-size: 4px;">sqlite.use - Use SQLiteLib commands<br />
</span><br />
<span style="font-size: 6px;"><strong>Storage:</strong></span><br />
<span style="font-size: 4px;">All database files are stored in the SQLiteLib folder within your plugins folder. You can manually edit/delete databases from there.</p>

<p></span><br />
<span style="font-size: 6px;"><strong>Suggestions & Support:</strong></span><br />
<span style="font-size: 4px;">If you have any suggestions, bugs, or need help, please post in the discussion of the resource! You may also join my discord for instant support:</span><br />
<span style="font-size: 5px;"><strong><a href="https://discord.gg/DpNFSQu">https://discord.gg/DpNFSQu</a></strong></p>

<p><span style="font-size: 6px;"><strong>Hooking into SQLiteLib:</strong></span></span><br />
<span style="font-size: 5px;"><span style="font-size: 4px;">Simply download the jar anywhere on your PC and add it to your project's build path. Once added, you can gain access to the library by using the following:</span></span><br />
</center><br />
<span style="font-size: 5px;"><span style="font-size: 4px;"><pre>public SQLiteLib sqlLib; 
@Override
public void onEnable() {
        sqlLib = SQLiteLib.hookSQLiteLib();
    }</pre></span></span><br />
<span style="font-size: 5px;"><span style="font-size: 4px;"></span></span></p>

<p><center></p>

<p><span style="font-size: 5px;"><span style="font-size: 6px;"><strong>Initializing Databases:</strong></span></span><br />
<span style="font-size: 5px;"><span style="font-size: 4px;">Once you've hooked into SQLiteLib, you can start using all the tools. First, we need to make sure we create/initialize a database.</span></span><br />
</center></p>

<p><center><span style="font-size: 5px;"><span style="font-size: 4px;"><pre>sqlLib.initializeDatabase("database_name", "CREATE TABLE IF NOT EXISTS table_name");</pre></span></span></p>

<p><span style="font-size: 5px;"><span style="font-size: 4px;">You can see, it requires 2 arguments. The <strong>database name</strong> & <strong>initial statement</strong>. The initial statement should be used to create your tables/rows/columns. This is just a basic create statement however they can be much more advanced if you understand SQL. This code should be ran every time your plugin requires a database. This method will create a database if it does not exist, but will also load the database if it does exist. <strong>It is required to use any other database methods. If the database isnt initialized, nothing else will work.</strong></span></span></center></p>

<p><span style="font-size: 4px;"><br />
</span><br />
<center><strong><span style="font-size: 6px;">Executing Statements:</span></strong><br />
<span style="font-size: 4px;">There may come a time during your SQL experience which requires you to execute ANY statement possible. I did not want to limit the developers using SQLiteLib, so I added this handy method in case you need to execute special statements which aren't usable in other methods. <br />
</span></center><br />
<span style="font-size: 4px;"><pre>if (sqlLib.getDatabase("name").executeStatement("statement")){
    // Executed statement successfully
}else{
    // Execution failure.
}</pre><br />
</span><br />
<center><span style="font-size: 6px;"><strong>Querying single values:</strong></span><br />
<span style="font-size: 4px;">You can easily query for any singular value using the following code:<br />
</span></center><br />
<span style="font-size: 4px;"><pre>String testValue = (String)sqlLib.getDatabase("test").queryValue("statement", "row");</pre></p>

<p>Notice how I am casting the returned statement as a string, as each returned value is an Object. As long as you cast properly, and don't attempt to cast random objects to other objects, you should be fine.</p>

<p>Here's an example of a database and query statement.</span></p>

<p>test.db:</p>

<p>-------<br />
| ID  |<br />
-------<br />
|  1   |<br />
|  2   |<br />
|  3   |<br />
-------</p>

<p>You can see this is a very simple table. It has 1 column called ID which contains their keys. 1-3. This can be casted to an Integer, Double, String, whatever you need basically. Anyways, here's my code to retrieve a single value.</p>

<p><pre>String test = (String)sqlLib.getDatabase("test").queryValue("SELECT * FROM test WHERE id = 1", "ID");</pre></p>

<p>You can see, I specify a statement that returns 1 value. I also specify the row I would like to read from the result, in this case, ID. You could also simply put "SELECT * FROM test, however, it would only return the first item in the list. Try to make your statements capture only 1 value, rather than a list.</p>

<p><center><span style="font-size: 6px;"><strong>Querying Row:</strong></span><br />
<span style="font-size: 4px;">If you'd like to query a list of results in a single row, you can use the following code. This will return List<Object>.<br />
</span></center><br />
<span style="font-size: 4px;"><pre>List<Object> results = sqlLib.getDatabase("test").queryRow("SELECT * FROM test", "ID");


for (Object obj : results){
   String ID = (String)obj;
   System.out.println("ID: "+ID);
}</pre></span></p>

<p><center><span style="font-size: 6px;"><strong>Query Map of Rows:</strong></span><br />
If you'd like to gather several values from rows, you can use queryMultipleRows. This will put together a map, which contains the row name, and a list of values. Map<row_name, List<Object>>.<br />
</center><br />
<pre>Map<String, List<Object>> rows = sqlLib.getDatabase("test").queryMultipleRows("SELECT * FROM test", "row1", "row 2", "row 3" );

String ID = (String)rows.get("test").get(0);</pre></p>

<p><center>If you need more help, join this server! <strong><a href="https://discord.gg/DpNFSQu">https://discord.gg/DpNFSQu</a></strong></center></p>