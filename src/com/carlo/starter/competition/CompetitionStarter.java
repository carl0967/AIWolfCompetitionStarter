package com.carlo.starter.competition;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;

import org.aiwolf.client.base.player.AbstractRoleAssignPlayer;
import org.aiwolf.common.data.Player;
import org.aiwolf.common.data.Role;
import org.aiwolf.common.net.GameSetting;
import org.aiwolf.common.util.CalendarTools;
import org.aiwolf.server.AIWolfGame;
import org.aiwolf.server.net.DirectConnectServer;
import org.aiwolf.server.net.GameServer;
import org.aiwolf.server.util.FileGameLogger;

import com.gmail.jinro.noppo.players.RoleAssignPlayer;
import com.yy.player.YYRoleAssignPlayer;

public class CompetitionStarter {
	/** 何回ゲームをするか */
	private  int gameNum = 1;
	ArrayList<Class> playerClasses=new ArrayList<Class>();
	Map<Class,RoleWinLoseCounter> winLoseCounterMap; 
	//Map<Class,String> classNameMap;
	/**
	 * @param gameNum 試合回数
	 */
	public CompetitionStarter(int gameNum){
		this.gameNum=gameNum;
		winLoseCounterMap=new LinkedHashMap<Class,RoleWinLoseCounter>();
		
	}
	public void addClass(Class playerClass){
		playerClasses.add(playerClass);
		winLoseCounterMap.put(playerClass, new RoleWinLoseCounter(playerClass.getSimpleName()));
	}
	public void addClass(Class playerClass,String name){
		playerClasses.add(playerClass);
		winLoseCounterMap.put(playerClass, new RoleWinLoseCounter(name));
	}
	public int getPlayerNum(){
		return playerClasses.size();
	}
	
	Map<Role,Double> averageMap;
	/**
	 * 
	 * @param isShowConsoleLog 人狼ゲームのコンソールログを表示するか
	 * @param isSaveGameLog ゲームログを保存するか
	 * @throws InstantiationException
	 * @throws IllegalAccessException
	 */
	public void gameStart(boolean isShowConsoleLog,boolean isSaveGameLog) throws InstantiationException, IllegalAccessException{
		RoleManager manager=new RoleManager(playerClasses.size());
		ArrayList<Role> roleList=manager.getRoleList();
		//System.out.println(roleList);
		
		//game 
		for(int i = 0;i<gameNum;i++){
			//roleListをシャッフル(役職を変えるため) NOTE:ランダムじゃなくて、各エージェントが同じ回数役職をやるように操作したほうがいい？
			Collections.shuffle(roleList);
			Map<Player, Role> playerMap = new HashMap<Player, Role>();
			Map<Class,Role> classMap=new HashMap<Class,Role>();
			for(int j=0;j<playerClasses.size();j++){
				playerMap.put((Player) playerClasses.get(j).newInstance(),roleList.get(j));
				classMap.put(playerClasses.get(j), roleList.get(j));
			}
			
			GameServer gameServer = new DirectConnectServer(playerMap);
			GameSetting gameSetting = GameSetting.getDefaultGame(playerClasses.size());
			AIWolfGame game = new AIWolfGame(gameSetting, gameServer);
			game.setShowConsoleLog(isShowConsoleLog);
			
			if(isSaveGameLog){
				String timeString = CalendarTools.toDateTime(System.currentTimeMillis()).replaceAll("[\\s-/:]", "");
				File logFile = new File(String.format("%s/aiwolfGame%s_%d.log", "./log/", timeString,i));
				try {
					game.setGameLogger(new FileGameLogger(logFile));
				} catch (IOException e) {
					// TODO 自動生成された catch ブロック
					e.printStackTrace();
				}
			}
			
			game.setRand(new Random(gameSetting.getRandomSeed()));
			game.start();
			
			//勝敗結果格納
			for(Map.Entry<Class, Role> entry : classMap.entrySet()) {
				Class playerClass=entry.getKey();
				Role playerRole=entry.getValue();
				winLoseCounterMap.get(playerClass).endGame(game.getWinner(), playerRole);
			}

		}
		//結果処理
	calcResult();
		
		
	}
	public void printwinLoseCounterMap(){
		System.out.println("プレイヤーの名前\t\t\t狩人\t霊能者\t狂人\t占い師\t村人\t人狼\t合計");
		for(Entry<Class, RoleWinLoseCounter> classEntry : winLoseCounterMap.entrySet()) {
			String name=classEntry.getValue().getName();
			int block= 4-(name.length()/8);
			if(name.length()%8!=0) block--;
			System.out.print(name);
			for(int i=0;i<block;i++){
				System.out.print("\t");
			}
			//ポイントの表示
			for(Role role:Role.values()){
				if(role==Role.FREEMASON) continue;
				System.out.printf("\t%.2f",classEntry.getValue().getPoint(role));
			}
			System.out.printf("\t%.2f",classEntry.getValue().getTotalPoint());
			System.out.println();
		}
		
	}
	/** 直下にcsvフォルダがなければ作り、csvを保存する */
	public void writeToCSVFile(){
		FileWriter fw;
		
		File dir=new File("./csv");
		if (!dir.exists()) {
			dir.mkdir();
		}
		
		String timeString = CalendarTools.toDateTime(System.currentTimeMillis()).replaceAll("[\\s-/:]", "");
		try {
			fw = new FileWriter("csv/"+timeString+".csv", false);
			PrintWriter pw = new PrintWriter(new BufferedWriter(fw));
			pw.println("試行回数,"+gameNum);
			pw.println("獲得勝利ポイント(勝率-平均勝率)");
			pw.println("PlayerName,狩人,霊能者,狂人,占い師,村人,人狼,合計");
			for(Entry<Class, RoleWinLoseCounter> classEntry : winLoseCounterMap.entrySet()) {
				pw.print(classEntry.getValue().getName());
				for(Role role:Role.values()){
					if(role==Role.FREEMASON) continue;
					pw.printf(",%.2f",classEntry.getValue().getPoint(role));
				}
				pw.printf(",%.2f",classEntry.getValue().getTotalPoint());
				pw.println();
			}
			pw.println("勝率");
			pw.println("PlayerName,狩人,霊能者,狂人,占い師,村人,人狼");
			for(Entry<Class, RoleWinLoseCounter> classEntry : winLoseCounterMap.entrySet()) {
				pw.print(classEntry.getValue().getName());
				for(Role role:Role.values()){
					if(role==Role.FREEMASON) continue;
					pw.printf(",%.2f",classEntry.getValue().getRate(role));
				}
				pw.println();
			}
			pw.println("勝利回数");
			pw.println("PlayerName,狩人,霊能者,狂人,占い師,村人,人狼");
			for(Entry<Class, RoleWinLoseCounter> classEntry : winLoseCounterMap.entrySet()) {
				pw.print(classEntry.getValue().getName());
				for(Role role:Role.values()){
					if(role==Role.FREEMASON) continue;
					pw.print(","+classEntry.getValue().getWinCount(role)+"/"+classEntry.getValue().getTotalCount(role));
				}
				pw.println();
			}
			
			pw.close();
		} catch (IOException e) {
			// TODO 自動生成された catch ブロック
			e.printStackTrace();
		}
	}
	

	private void calcResult(){
		calcAverage();
		for(Entry<Class, RoleWinLoseCounter> entry : winLoseCounterMap.entrySet()) {
			entry.getValue().calcPointMap(averageMap);
		}
	}
	private void calcAverage(){
		averageMap=new HashMap<Role,Double>();
		for(Role role:Role.values()){
			if(role==Role.FREEMASON) continue;
			int div=playerClasses.size();
			double tmp=0;
			for(Entry<Class, RoleWinLoseCounter> classEntry : winLoseCounterMap.entrySet()) {
				tmp+=classEntry.getValue().getRate(role);
			}
			double  average=tmp/div;
			
			averageMap.put(role, average);
		}
	}

	public static void main(String[] args) throws ClassNotFoundException, InstantiationException, IllegalAccessException {
		//ゲーム試行回数
		int gameNum=10;
		if(args.length==1) gameNum=Integer.parseInt(args[0]);
		CompetitionStarter starter=new CompetitionStarter(gameNum);
		
		//プレイヤークラスの追加
		starter.addClass(Class.forName("org.aiwolf.client.base.smpl.SampleRoleAssignPlayer"));
		starter.addClass(Class.forName("com.yy.player.YYRoleAssignPlayer"));
		starter.addClass(Class.forName("jp.halfmoon.inaba.aiwolf.strategyplayer.StrategyPlayer"),"饂飩"); 
		starter.addClass(Class.forName("org.aiwolf.kajiClient.LearningPlayer.KajiRoleAssignPlayer"));
		starter.addClass(Class.forName("com.gmail.jinro.noppo.players.RoleAssignPlayer"),"働きの悪"); 
		starter.addClass(Class.forName("org.aiwolf.Satsuki.LearningPlayer.AIWolfMain"),"Satuki"); 
		starter.addClass(Class.forName("jp.ac.shibaura_it.ma15082.WasabiPlayer"),"Wasabi"); 
		starter.addClass(Class.forName("com.carlo.bayes.player.BayesPlayer"));
		starter.addClass(Class.forName("takata.player.TakataRoleAssignPlayer"),"GofukuLab");
		starter.addClass(Class.forName("ipa.myAgent.IPARoleAssignPlayer"));
		starter.addClass(Class.forName("org.aiwolf.iace10442.ChipRoleAssignPlayer"),"iace10442"); 
		starter.addClass(Class.forName("kainoueAgent.MyRoleAssignPlayer"),"swingby"); //swingby
		//starter.addClass(Class.forName("jp.ac.aitech.k13009kk.aiwolf.client.player.AndoRoleAssignPlayer")); //itolab //ログ出力
		starter.addClass(Class.forName("com.github.haretaro.pingwo.role.PingwoRoleAssignPlayer"),"平兀"); 
		starter.addClass(Class.forName("com.gmail.the.seventh.layers.RoleAssignPlayer"),"Fenrir"); 
		starter.addClass(Class.forName("jp.ac.cu.hiroshima.info.cm.nakamura.player.NoriRoleAssignPlayer"),"中村人");
		//starter.addClass(Class.forName("com.gmail.octobersky.MyRoleAssignPlayer")); //昼休みはいつも人狼でつぶれる
		//starter.addClass(Class.forName("com.canvassoft.Agent.CanvasRoleAssignPlayer")); //CanvasSoft //ログ
		
		System.out.println(starter.getPlayerNum()+"人");
		starter.gameStart(false,true);
		starter.printwinLoseCounterMap();
		starter.writeToCSVFile();
	}

}
