package me.raniy.plugins.UserHistory;

import java.util.logging.Logger;
import java.util.HashMap;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.Event.Priority;
import org.bukkit.event.player.PlayerListener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.util.config.Configuration;
import org.bukkit.ChatColor;

@SuppressWarnings("unused")
public class UserHistory extends JavaPlugin {
	private Logger log=Logger.getLogger("Minecraft");
	public Configuration myConfig = null;
	public Connection myMySQL = null;
	public Statement myStatement = null;
	public PluginDescriptionFile myDesc = null;
	public UserHistoryPlayerListener myListener = null;
	private String MySQLUser = null, MySQLPass = null, MySQLIP = null, MySQLPort = null;
	private String MySQLDB = null;
	public java.util.Properties myProperties= null;
	private String[] allKnownPlayers = null;
	
	
    public void onDisable() {
        this.doLog("Disabled.");
    }

    public void onEnable() {  
    	
    	// Load the ymls
    	this.getMySettings();
    	
    	// Get MySQL
    	this.getMySQL();
    	
    	// Check the SQL Tables
    	this.checkMySQL();
    	
    	// Close it again
    	this.closeMySQL();
    	
    	// Get the listener
    	this.myListener = new UserHistoryPlayerListener(this);
    	
    	// Register listeners
    	this.registerListeners();
    	
    	// Tell the console we activated.
    	this.doLog("Enabled.");
    	
    }
    
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args)
    {
		//Check sender / get Player
    	Player player = null;
    	if (sender instanceof Player) 
    	{
    		player = (Player) sender;
    	}
    	
    	// Do MyAge. First command. Gives very limited access to some of the data we have been collecting.
    	if ((command.getName().equalsIgnoreCase("MYAGE")) && (!(player == null)))
    	{ 	//If the player typed /myage
    		
    		// Get MySQL
        	this.getMySQL();
        	
    		// Assume their age is 0
    		long currentAge = 0, totalAge = 0;
    		
    		//Current Age should be the difference between the current time and their last online time
    		currentAge = System.currentTimeMillis() - (this.playerLastOnline(player)).getTime();
    		
    		// Total Age should be their age in the db + their current age.
    		totalAge = this.getPlayerAge(player) + currentAge;
    		
    		//Tell them.
    		player.sendMessage(ChatColor.WHITE + "You logged on " + ChatColor.GREEN + this.timeToString(currentAge) + ChatColor.WHITE + " ago at: " + ChatColor.GREEN + (this.playerLastOnline(player)).toString() + ChatColor.WHITE);
    		player.sendMessage(ChatColor.WHITE + "You have played a total of: " + ChatColor.GREEN + this.timeToString(totalAge) + ChatColor.WHITE + " since: " + ChatColor.GREEN + this.playerFirstOnline(player) + ChatColor.WHITE);
    		
    		// Close MySQL
    		this.closeMySQL();
    		
    		// Tell Bukkit we handled this command
    		return true;
    	} else if ((command.getName().equalsIgnoreCase("LASTSEEN")) && (!(player == null))) {
    		// // Doing LastSeen. Returns the last time a player was seen. With idiot checking.
    		if (args.length > 0)
    		{	//They gave an argument
    			String theName = args[0];
    			Player[] PlayerList = null;
    			Player targetPlayer = null;
    			
    			// Idiot Checking: See if target is online.	
   				targetPlayer = this.getServer().getPlayerExact(theName);
    			 
   				if (targetPlayer != null)
   				{
   					//The target is online! Silly players.
   					// Real idiot checking. Is targetPlayer sender? :)
   					if(player == targetPlayer)
   					{
   						// LOL. Tell them we are not amused.
   						sender.sendMessage("Have you gone missing?");
   						return true;
   					}
   					sender.sendMessage(targetPlayer.getName() + " is online now.");
   					return true;
   				}
   				
    			// Try to find target in the mcusers table
    			this.getMySQL();
    			int sqlUserID = 0;
    			sqlUserID = this.playerInDB(theName);
    			if(sqlUserID > 0)
    			{		//Found a matching name
    				//Adjust our variable to be 'Proper'
    				theName = this.getProperPlayerNameByID(sqlUserID);
    				String targetLastSeen = this.timeToString(this.getLastSeen(theName));
    				sender.sendMessage(ChatColor.GREEN + theName + ChatColor.WHITE + " was last seen " + ChatColor.GREEN + targetLastSeen + ChatColor.WHITE + " ago at " + ChatColor.GREEN + this.playerLastOffline(theName).toString() + ChatColor.WHITE);
    				this.closeMySQL();
    				return true;
    			}
    			this.closeMySQL();
    				
    			if (targetPlayer == null) 
    			{
    				// Inform the player we couldnt find a match 
    				sender.sendMessage("I dont recognize: '" + theName + "' as a valid player name. Did you spell it wrong?");
    				return true;
    			}
    		} else {
    			sender.sendMessage("You must specify a playername to use lastseen.");
    			return true;
    		}
    		// Logically we only made hit here if we have had a logic error... In that case tell Bukkit we didnt do anything.
    		return false;
    	} else {
    		// Tell Bukkit we did not handle this command
    		return false;
    	}
    	
    }
    
    private void getMySettings(){
    	this.myDesc = this.getDescription();
    	this.myConfig = this.getConfiguration();
    	this.myProperties = new java.util.Properties();
    	// Load our startup variables, set defaults if no config was present
    	this.MySQLUser = this.myConfig.getString("MySQL.User","minecraft");
    	this.MySQLPass = this.myConfig.getString("MySQL.Pass","password");
    	this.MySQLIP = this.myConfig.getString("MySQL.IP","127.0.0.1");
    	this.MySQLPort = this.myConfig.getString("MySQL.Port","3306");
    	this.setMySQLDB(this.myConfig.getString("MySQL.DB","minecraft"));
    	//Make sure the config file is created.
    	this.myConfig.save();

    }
    
    private void registerListeners(){
    	// Get PluginManager
    	PluginManager pm=this.getServer().getPluginManager();
    	
    	// Register the events we want to track. At this time thats log ins and outs
    	pm.registerEvent(Event.Type.PLAYER_JOIN, this.myListener, Priority.Normal, this);
    	pm.registerEvent(Event.Type.PLAYER_QUIT, this.myListener, Priority.Normal, this);
    }
    
    private String getJDBCString(String strIP, String strPort, String strDB){
    	String retval = "jdbc:mysql://" + strIP + ":" + strPort + "/" + strDB;
    	return(retval); 
    }
    
    public void getMySQL(){
    	try {
    		// This will load the MySQL driver
    		Class.forName("com.mysql.jdbc.Driver");
			this.myMySQL  = DriverManager.getConnection(this.getJDBCString(this.MySQLIP, this.MySQLPort, this.getMySQLDB()), this.MySQLUser, this.MySQLPass);
			this.myStatement = this.myMySQL.createStatement();
			
		} catch (SQLException e) {
			this.doLog("SQL Error! " + e.getMessage());
		} catch (ClassNotFoundException e) {
			this.doLog("Java Error! " + e.getMessage());
		}
    }
    
    public void closeMySQL(){
    	try {
			this.myMySQL.close();
		} catch (SQLException e) {
			this.doLog("SQL Error! " + e.getMessage());
		}
    }
    
    private void checkMySQL()
    {
    	// Check the MySQL database we have been told to use. Make sure our tables exist.   	
    	try {
    		// mcusers first.
        	String myQuery;
        	myQuery = "CREATE TABLE IF NOT EXISTS `mcusers` (`UserID` int(11) NOT NULL AUTO_INCREMENT,`UserMCName` varchar(255) NOT NULL,PRIMARY KEY (`UserID`)) ENGINE=InnoDB  DEFAULT CHARSET=utf8;";
			this.myStatement.executeUpdate(myQuery);
	    	// mcuserslogins
	    	myQuery = "CREATE TABLE IF NOT EXISTS `mcuserslogin` (`loginID` int(11) NOT NULL AUTO_INCREMENT,`UserID` int(11) NOT NULL,`SeenIP` varchar(15) NOT NULL,`LoginTIME` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,PRIMARY KEY (`loginID`),KEY `UserID` (`UserID`)) ENGINE=InnoDB  DEFAULT CHARSET=utf8;";
	    	this.myStatement.executeUpdate(myQuery);
	    	// mcuserslogouts
	    	myQuery = "CREATE TABLE IF NOT EXISTS `mcuserslogout` (`logoutID` int(11) NOT NULL AUTO_INCREMENT,`UserID` int(11) NOT NULL,`SeenIP` varchar(15) NOT NULL,`logoutTIME` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,UNIQUE KEY `logoutID` (`logoutID`)) ENGINE=InnoDB  DEFAULT CHARSET=utf8;";
	    	this.myStatement.executeUpdate(myQuery);
	    	// mcusersage
	    	myQuery = "CREATE TABLE IF NOT EXISTS `mcusersage` (`UserID` int(11) NOT NULL,`TotalAge` bigint(20) NOT NULL,`LastUpdated` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,PRIMARY KEY (`UserID`)) ENGINE=InnoDB DEFAULT CHARSET=utf8;";
	    	this.myStatement.executeUpdate(myQuery);
	    	
		} catch (SQLException e) {
			this.doLog("SQL Error! " + e.getMessage());
		}
    }
    
	public String getMySQLDB() 
	{
		return this.MySQLDB;
	}

	public void setMySQLDB(String mySQLDB) 
	{
		this.MySQLDB = mySQLDB;
	}
    
	public void doLog(String strLog)
	{
		//ToDo: Should probably check to make sure we are only logging events from us
		this.log.info("[" + this.myDesc.getName() + "] " + strLog);
	}
	
	public Timestamp playerLastOnline(Player player)
	{
		Timestamp retval = null;
		this.getMySQL();
		
		ResultSet rs;
		String myQuery = "SELECT * FROM  `mcuserslogin` WHERE  `UserID` = " + (this.playerInDB(player)) + " ORDER BY loginID DESC LIMIT 1;";
		try {
			rs = this.myStatement.executeQuery(myQuery);
			if (rs.next()){			
				retval = (rs.getTimestamp("LoginTIME"));
			}
		} catch (SQLException e) {
			this.doLog("SQL Error! " + e.getMessage());
		}
		
		return(retval);	
	}
	
	public long playerLastOnlineTime(Player player)
	{
		long retval = this.timestampDiff(this.playerLastOffline(player),this.playerLastOnline(player));
		return(retval);
		
	}
	
	public Timestamp playerLastOffline(Player player){
		Timestamp retval = null;
		this.getMySQL();
		
		ResultSet rs;
		String myQuery = "SELECT * FROM  `mcuserslogout` WHERE  `UserID` = " + (this.playerInDB(player)) + " ORDER BY logoutID DESC LIMIT 1;";
		try {
			rs = this.myStatement.executeQuery(myQuery);
			if (rs.next()){			
				retval = (rs.getTimestamp("logoutTIME"));
			}
		} catch (SQLException e) {
			this.doLog("SQL Error! " + e.getMessage());
		}
		
		return(retval);	
	}
	
	public Timestamp playerLastOffline(String thePlayersName){
		Timestamp retval = null;
		this.getMySQL();
		
		ResultSet rs;
		String myQuery = "SELECT * FROM  `mcuserslogout` WHERE  `UserID` = " + (this.playerInDB(thePlayersName)) + " ORDER BY logoutID DESC LIMIT 1;";
		try {
			rs = this.myStatement.executeQuery(myQuery);
			if (rs.next()){			
				retval = (rs.getTimestamp("logoutTIME"));
			}
		} catch (SQLException e) {
			this.doLog("SQL Error! " + e.getMessage());
		}
		
		return(retval);	
	}
	
	public Timestamp playerFirstOnline(Player player)
	{
		Timestamp retval =null;
		this.getMySQL();
		
		ResultSet rs;
		String myQuery = "SELECT * FROM  `mcuserslogin` WHERE  `UserID` = " + (this.playerInDB(player)) + " ORDER BY loginID ASC LIMIT 1;";
		try {
			rs = this.myStatement.executeQuery(myQuery);
			if (rs.next()){			
				retval = (rs.getTimestamp("LoginTIME"));
			}
		} catch (SQLException e) {
			this.doLog("SQL Error! " + e.getMessage());
		}      
		return(retval);	
	}
	
	public Player getOnlinePlayerByIP(String IP){
		Player thePlayer = null;
		Player onlinePlayers[] = this.getServer().getOnlinePlayers();
		
		for(int cnt = 0; cnt < onlinePlayers.length; cnt++) 
			  if (onlinePlayers[cnt].getAddress().getAddress().getHostAddress().equalsIgnoreCase(IP)){
				  thePlayer = onlinePlayers[cnt];
			  }
				
		return thePlayer;
	}
	
	public long timestampDiff(Timestamp ts1, Timestamp ts2){
    	long retval = (ts1.getTime()) - (ts2.getTime());    	
    	return(retval);
    }
    
    public String timeToString(long time){/* 
    	 * Convert the input time in milliseconds, 
    	 *		to a human readable String of the type '1y 11m 3w 29d 23h 59m 59s'
    	 *	Due to the method used it is inaccurate 
    	 *		to use this for calculating years. As leap years are being ignored. 
    	 *	Only returns bits of the string that are non zero.
    	 *  Feels like this function was a program in itself. Ill have to save it for my utils.
    	 */
    	
    	final long Second = 1000;
    	final long Minute = Second*60;
    	final long Hour = Minute*60;
    	final long Day = Hour*24;
    	final long Week = Day*7;
    	final long Month = Week*4;
    	final long Year = Day*365;
    	
    	long PlaceHolder, myTime, Years, Months, Weeks, Days, Hours, Minutes, Seconds;
    	
    	//Start retval off empty
    	String retval = "";
    	
    	// Strip out Years
    	myTime = time;
    	PlaceHolder = (time%Year);
    	Years = (time-PlaceHolder) / Year;
    	
    	// If Years is more then 0 add it to retval
    	if (Years > 0)
    	{
    		retval += Long.toString(Years) + "y "; 
    	}
    	
    	// Strip out Months
    	myTime = PlaceHolder;
    	PlaceHolder = (myTime%Month);
    	Months = ((time - PlaceHolder) / Month);
    	
    	// if Months, or Years is more then 0 add it to retval
    	if ((Months > 0) || Years > 0)
    	{
    		retval += Long.toString(Months) + "m ";
    	}
    	
    	// Strip out Weeks
    	myTime = PlaceHolder;
    	PlaceHolder = (myTime%Week);
    	Weeks = ((time - PlaceHolder) / Week);
    	
    	// If Weeks, or Months, or Years, is more then 0 add it to retval
    	if ((Weeks > 0) || (Months > 0) || (Years > 0))
    	{
    		retval += Long.toString(Weeks) + "w ";
    	}
    	
    	// Strip out Days
    	myTime = PlaceHolder;
    	PlaceHolder = (time%Day);
    	Days = ( (time - PlaceHolder) / Day);
    	
    	// If Days, or Weeks, or Months, or Years, is more then 0 add it to retval
    	if ((Days > 0) || (Weeks > 0) || (Months > 0) || (Years > 0))
    	{ 
    		retval = Long.toString(Days) + "d ";
    	}
    	
    	// Strip out Hours
    	myTime = PlaceHolder;
    	PlaceHolder = (myTime%Hour);
    	Hours = ( (myTime - PlaceHolder) / Hour);		

    	// If Hours, Days, Weeks, Months, Years is more then 0 += retval
    	if ((Hours > 0) || (Days > 0) || (Weeks > 0) || (Months > 0) || (Years > 0))
    	{
    		retval += Long.toString(Hours) + "h ";
    		
    	}
    	
    	// Minutes
    	myTime = PlaceHolder;
    	PlaceHolder = (myTime%Minute);
    	Minutes = ( (myTime - PlaceHolder) / Minute);
    	
    	// Last few comments make me think I must be getting lazy. 
    	// Etc etc etc...
    	if ((Minutes > 0) || (Hours > 0) || (Days > 0) || (Weeks > 0) || (Months > 0) || (Years > 0)){
    		retval += Long.toString(Minutes) + "m ";
    	}
    	
    	// Strip out Seconds
    	myTime = PlaceHolder;
    	PlaceHolder = (myTime%Second);
    	Seconds = ( (myTime - PlaceHolder) / Second);
    	
    	// We can safely assume there should always be a return of at least 0s
    	retval += Long.toString(Seconds) + "s";	
    	return (retval);
    }
    
	public int playerInDB(Player player){
    	
    	//we need to see if this Player is already in the database
    	// Assume they are not
    	int retval = 0;
    	
    	ResultSet rs;
    	try {			
			String myQuery = "SELECT * FROM `" + this.getMySQLDB() + "`.`mcusers` WHERE `UserMCName` = '" + player.getName() + "';"; 
			//this.myPlugin.log.info("SQL Query: " + myQuery);
			
			rs = this.myStatement.executeQuery(myQuery);
	    	
	    	if (rs.next())
	    	{
	    		// They Exist
	    		retval = rs.getInt("UserID");
	    	}
		} catch (SQLException e) {
			this.doLog("SQL Error! " + e.getMessage());
		}
    	return(retval);
    	
    }
	
	public int playerInDB(String playername){
    	
    	//we need to see if this Player is already in the database
    	// Assume they are not
    	int retval = 0;
    	
    	ResultSet rs;
    	try {			
			String myQuery = "SELECT * FROM `" + this.getMySQLDB() + "`.`mcusers` WHERE lower(`UserMCName`) LIKE lower('" + playername + "');"; 
			
			rs = this.myStatement.executeQuery(myQuery);
	    	
	    	if (rs.next())
	    	{
	    		// They Exist
	    		retval = rs.getInt("UserID");
	    	}
		} catch (SQLException e) {
			this.doLog("SQL Error! " + e.getMessage());
		}
    	return(retval);
    	
    }
    
	public long getPlayerTotalLogins(Player player)
    {
    	ResultSet rs = null;
    	long playerTotalLogins = 0;
    	
		String myQuery = "SELECT * FROM  `mcuserslogin` WHERE  `UserID` = " + (this.playerInDB(player)) + ";";
		try {
			rs = this.myStatement.executeQuery(myQuery);
			
			while (rs.next()){
				playerTotalLogins += 1;
			}
			
		} catch (SQLException e) {
			this.doLog("SQL Error! " + e.getMessage());
		}
		
		
		return playerTotalLogins;
    	
    }
    
    public void updatePlayerAge(Player thePlayer, long playerOnFor) {
		final long newPlayerAge = getPlayerAge(thePlayer) + playerOnFor;
		final String myQuery = "UPDATE `" + this.getMySQLDB() + "`.`mcusersage` SET `TotalAge`='" + Long.toString(newPlayerAge) + "' WHERE `UserID` = '" + this.playerInDB(thePlayer) + "';";    	
    	try {
    		int rs;
    		rs = this.myStatement.executeUpdate(myQuery);
    	} catch (SQLException e) {
			this.doLog("SQL Error! " + e.getMessage());
		}
		
	}
    
    public long getPlayerAge(Player player){

		String myQuery = "";
		ResultSet rs = null;
		long playerAge = 0;
    	try {
			myQuery = "SELECT * FROM `" + this.getMySQLDB() + "`.`mcusersage` WHERE `UserID` = '" + this.playerInDB(player) + "';";
    		rs = this.myStatement.executeQuery(myQuery);
    		rs.next();
    		playerAge = rs.getLong("TotalAge");
		} catch (SQLException e) {
			this.doLog("SQL Error! " + e.getMessage());
		}
    	return playerAge;
    }

    public long getLastSeen(Player thePlayer)
    {
    	/* Returns 0 if the Player is online now. Otherwise returns the time in millis since last seen. */
    	/*Removed for logic reasons
    	 
    	
    	if (thePlayer.isOnline())
    	{	// return 0
    		return retval;
    	} else  {*/
    	long retval = 0;
    	//should be the difference between the current time and their last offline time
    	long lastOff = (this.playerLastOffline(thePlayer)).getTime();   	
    	retval = System.currentTimeMillis() - lastOff;
    	return retval;
     }
    
    public long getLastSeen(String thePlayersName)
    {
    	/* String version: Doesnt check for online, just gets the db info. 
    	 * Returns the time in millis since last seen. */
    	long retval = 0;
        		//should be the difference between the current time and their last offline time
    		long lastOff = (this.playerLastOffline(thePlayersName)).getTime();   	
    		retval = System.currentTimeMillis() - lastOff;
    		return retval;
    }
    
    public String getProperPlayerNameByID(int UserID){
    	String retval = "";
    	ResultSet rs;
    	try {			
			String myQuery = "SELECT * FROM `" + this.getMySQLDB() + "`.`mcusers` WHERE `UserID` = '" + UserID + "';"; 
			//this.myPlugin.log.info("SQL Query: " + myQuery);
			
			rs = this.myStatement.executeQuery(myQuery);
	    	
	    	if (rs.next())
	    	{
	    		// They Exist
	    		retval = rs.getString("UserMCName");
	    	}
		} catch (SQLException e) {
			this.doLog("SQL Error! " + e.getMessage());
		}
    	
    	return retval;   	
    }
}
