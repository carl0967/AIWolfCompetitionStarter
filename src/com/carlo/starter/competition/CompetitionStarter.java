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
	
	Map<Class,Integer> classNumMap;
	/**
	 * @param gameNum 試合回数
	 */
	public CompetitionStarter(int gameNum){
		this.gameNum=gameNum;
		winLoseCounterMap=new LinkedHashMap<Class,RoleWinLoseCounter>();
		
		classNumMap=new HashMap<Class,Integer>();
		
	}
	public void addClass(Class playerClass){
		addClass(playerClass,playerClass.getSimpleName(),1);
	}
	public void addClass(Class playerClass,String name){
		addClass(playerClass,name,1);
	}
	/**
	 * 
	 * @param playerClass エージェントのクラスファイル
	 * @param name エージェント名。指定がなければクラス名で表示される。
	 * @param num そのクラスのエージェントを何体ゲームに参加させるか
	 */
	public void addClass(Class playerClass,String name,int num){
		playerClasses.add(playerClass);
		winLoseCounterMap.put(playerClass, new RoleWinLoseCounter(name));
		classNumMap.put(playerClass, num);
	}
	/**
	 * 
	 * @return ゲームに参加する総エージェント数（同一クラスを含む）
	 */
	public int getPlayerNum(){
		int playerNum=0;
		for(Entry<Class,Integer> classEntry : classNumMap.entrySet()) {
			playerNum+=classEntry.getValue();
		}
		return playerNum;
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
		RoleManager manager=new RoleManager(getPlayerNum());
		ArrayList<Role> roleList=manager.getRoleList();
		//System.out.println(roleList);
		
		//game 
		for(int i = 0;i<gameNum;i++){
			//今どれだけゲームを行ったかを表示
			if(i%1000==0) System.out.println(i+"/"+gameNum);
			//roleListをシャッフル(役職を変えるため) 
			Collections.shuffle(roleList);
			
			Map<Player, Role> playerMap = new HashMap<Player, Role>();
			//Roleを順番にとってくるためのインデックス
			int j=0;
			//登録されたエージェントクラスごとの処理
			for(Entry<Class,Integer> classEntry : classNumMap.entrySet()) {
				//指定された数参加させる
				for(int k=0;k<classEntry.getValue();k++){
					playerMap.put((Player)classEntry.getKey().newInstance(),roleList.get(j));
					j++;
				}
			}
			
			GameServer gameServer = new DirectConnectServer(playerMap);
			GameSetting gameSetting = GameSetting.getDefaultGame(getPlayerNum());
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
			for(Map.Entry<Player, Role> entry :playerMap.entrySet()){
				Class playerClass=entry.getKey().getClass();
				Role playerRole=entry.getValue();
				//System.out.println(winLoseCounterMap.get(playerClass).getName()+playerRole);
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
			//タブの位置を調整しようと頑張った結果
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
		int gameNum = 10;
		//ゲームログを保存するか
		boolean isSaveLog=false;
		//ゲームの経過をコンソールで表示するか
		boolean isShowConsoleLog=false;
		
		// コマンドライン引数で、動作を変えることもできます。
		// 例 -n 100 -l -s	:ゲーム試行回数100回、ゲームログを保存、コンソールログを表示
		// 例　-n 10		:ゲーム試行回数10回、ゲームログを保存しない(デフォルト)、コンソールログを表示しない(デフォルト)
		for (int i = 0; i < args.length; i++) {
			if (args[i].startsWith("-")) {
				if (args[i].equals("-n")) {
					i++;
					gameNum = Integer.parseInt(args[i]);
				}
				else if(args[i].equals("-l")){
					isSaveLog=true;
				}
				else if(args[i].equals("-s")){
					isShowConsoleLog=true;
				}
			}
		}
		
		CompetitionStarter starter=new CompetitionStarter(gameNum);
		
		//プレイヤークラスの追加
		//サンプルプレイヤーを1ゲームに３体追加する
		//複数体追加した場合、結果の集計は合算
		starter.addClass(Class.forName("org.aiwolf.client.base.smpl.SampleRoleAssignPlayer"),"サンプル",3);
		//YYを2体
		starter.addClass(Class.forName("com.yy.player.YYRoleAssignPlayer"),"YY",2);
		starter.addClass(Class.forName("jp.halfmoon.inaba.aiwolf.strategyplayer.StrategyPlayer"),"饂飩"); 
		//starter.addClass(Class.forName("org.aiwolf.kajiClient.LearningPlayer.KajiRoleAssignPlayer"));
		//starter.addClass(Class.forName("com.gmail.jinro.noppo.players.RoleAssignPlayer"),"働きの悪"); 
		starter.addClass(Class.forName("org.aiwolf.Satsuki.LearningPlayer.AIWolfMain"),"Satuki"); 
		starter.addClass(Class.forName("jp.ac.shibaura_it.ma15082.WasabiRoleAssignPlayer"),"Wasabi"); 
		starter.addClass(Class.forName("takata.player.TakataRoleAssignPlayer"),"GofukuLab");
		starter.addClass(Class.forName("ipa.myAgent.IPARoleAssignPlayer"),"IPA");
		starter.addClass(Class.forName("org.aiwolf.iace10442.ChipRoleAssignPlayer"),"iace10442"); 
		starter.addClass(Class.forName("kainoueAgent.MyRoleAssignPlayer"),"swingby"); //swingby
		//starter.addClass(Class.forName("jp.ac.aitech.k13009kk.aiwolf.client.player.AndoRoleAssignPlayer")); 
		starter.addClass(Class.forName("com.github.haretaro.pingwo.role.PingwoRoleAssignPlayer"),"平兀"); 
		starter.addClass(Class.forName("com.gmail.the.seventh.layers.RoleAssignPlayer"),"Fenrir"); 
		
		starter.addClass(Class.forName("jp.ac.cu.hiroshima.info.cm.nakamura.player.NoriRoleAssignPlayer"),"中村人");
		//starter.addClass(Class.forName("com.gmail.octobersky.MyRoleAssignPlayer"),"昼休み"); 
		//starter.addClass(Class.forName("com.canvassoft.Agent.CanvasRoleAssignPlayer")); //CanvasSoft 
		//starter.addClass(Class.forName("jp.ac.cu.hiroshima.inaba.agent.InabaAgent"),"chime"); 
		
		System.out.println(starter.getPlayerNum()+"人で"+gameNum+"回ゲームを行います。");
		starter.gameStart(isShowConsoleLog,isSaveLog);
		//結果をコンソールログで表示
		starter.printwinLoseCounterMap();
		//CSVファイルを生成
		starter.writeToCSVFile();
	
	}

}