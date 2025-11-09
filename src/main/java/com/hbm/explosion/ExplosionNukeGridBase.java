package com.hbm.explosion;

import com.hbm.interfaces.IExplosionRay;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.World;

import java.util.*;

import static java.lang.Math.random;
import static java.lang.Math.sqrt;

public class ExplosionNukeGridBase implements IExplosionRay {

	private static final int WORLD_HEIGHT = 256;
	private static final float BORDER = 0.0001f;
	private static final double MAX_RESISTANCE = 600000;

	// 爆発に必要な基本情報
	private World world;
	private int placeX = 0, placeY = 0, placeZ = 0;
	private int strength;
	private int length;
	private int speed;

	// 処理に使う具体的な変数
	// チャンク同士をつなぐ各方角の境界面マップ
	// W: -X, E: +X, N: -Z, S: +Z
	private HashMap<Integer, float[][][]> bridgehMapW, bridgehMapE, bridgehMapN, bridgehMapS;

	private int maxDistanceChunks;
	private TreeMap<Integer, ArrayList<Chunk>> scheduleChunks; //処理予定チャンクリスト
	private Iterator<Chunk> currentLayer;

	// 完了したかどうか
	private boolean complete;
	// 中心の処理時用
	private boolean firstSteps;

	/**
	 * 大爆発を処理するオブジェクト
	 */
	public ExplosionNukeGridBase(World world, int x, int y, int z, int strength, int speed, int length) {
		this.world = world;
		this.placeX = x;
		this.placeY = y;
		this.placeZ = z;

		this.strength = strength;
		this.speed = speed;
		this.length = length;

		this.bridgehMapW = new HashMap<Integer, float[][][]>();
		this.bridgehMapE = new HashMap<Integer, float[][][]>();
		this.bridgehMapN = new HashMap<Integer, float[][][]>();
		this.bridgehMapS = new HashMap<Integer, float[][][]>();


		this.complete = false;

		this.firstSteps = false;

		// scheduleChunkの準備
		this.scheduleChunks = new TreeMap<Integer, ArrayList<Chunk>>();
		this.addCircleMap(0,0);

		this.maxDistanceChunks = (this.length >> 4)+2; // 最大距離
		this.maxDistanceChunks *= this.maxDistanceChunks;
	}


	// 爆発処理
	// チャンクのグリッドを処理
	private void chunkGrid(Chunk chunk){
		// チャンクグリッドのスタート点
		int spX = this.placeX  & 0x0F;
		int spY = this.placeY;
		int spZ = this.placeZ  & 0x0F;

		// 各チャンクにおいて相対的な中心
		int centerX = spX - (chunk.x << 4);
		int centerY = spY;
		int centerZ = spZ - (chunk.z << 4);

		// 中心チャンク以外は端に寄せる
		spX = (chunk.x > 0) ? 0 : (chunk.x < 0) ? 15 : spX;
		spZ = (chunk.z > 0) ? 0 : (chunk.z < 0) ? 15 : spZ;

		// 限界高度若しくは0以下での高度の場合は範囲内に寄せる
		spY = (spY <= 0) ? 0 : (spY >= WORLD_HEIGHT) ? WORLD_HEIGHT-1 : spY;

		// グリッド用意
		float[][][] grid = new float[16][WORLD_HEIGHT][16];
		float[][][] gridMap = new float[16][WORLD_HEIGHT][16];
		boolean[][][] blockMap = new boolean[16][WORLD_HEIGHT][16];

		Deque<ChunkPosition> expandPoints = new LinkedList<ChunkPosition>();

		//境界面
		float[][][][] bridgeGrid = new float[4][2][16][WORLD_HEIGHT];
		float[][][] bg;

		if (chunk.x == 0 && chunk.z == 0 && this.placeY >= 0 && this.placeY < WORLD_HEIGHT) {
			// 中心チャンクでなおかつ高度範囲内
			// 一番最初の爆発で加算するか
			this.firstSteps = true;
		}
		// ここでグリッドでの開始位置に
		expandPoints.add(new ChunkPosition(spX,spY,spZ));

		while (!expandPoints.isEmpty()) {
			Deque<ChunkPosition> newPoints = new LinkedList<ChunkPosition>();
			for (ChunkPosition point : expandPoints) {
				for (ChunkPosition newPoint : this.getPeripheral(point)) {
					if (newPoint.chunkPosX >= 0 && newPoint.chunkPosX < 16 && newPoint.chunkPosY >= 0 && newPoint.chunkPosY < WORLD_HEIGHT && newPoint.chunkPosZ >= 0 && newPoint.chunkPosZ < 16) {
						if (grid[newPoint.chunkPosX][newPoint.chunkPosY][newPoint.chunkPosZ] <= 0) { // 未使用のセルのみ更新
							float newValue = 1.0F;
							float mapValue = 1.0F;

							float ran = (float) (1 * random());

							//ワールド座標に変換したもの
							int worldX = newPoint.chunkPosX - centerX + this.placeX;
							int worldY = newPoint.chunkPosY;
							int worldZ = newPoint.chunkPosZ - centerZ + this.placeZ;

							// 始点に向かうベクトル
							int directionX = centerX - newPoint.chunkPosX;
							int directionY = centerY - newPoint.chunkPosY;
							int directionZ = centerZ - newPoint.chunkPosZ;

							int sideX = Integer.compare(directionX, 0);
							int sideY = Integer.compare(directionY, 0);
							int sideZ = Integer.compare(directionZ, 0);

							{
								// 前後左右上下	：X+- YZ0, Y+- XZ0, Z+- XY0
								// 斜め			：YZ+- X0, XZ-- Y0, XY+- Z0
								// 角			：XYZ+-

								// 境界面向き
								// -x : 0 , W
								// +x : 1 , E
								// -z : 2 , N
								// +z : 3 , S

								float side1_value = 0;
								float side2_value = 0;
								float side3_value = 0;
								float map1_value = 0;
								float map2_value = 0;
								float map3_value = 0;

								// 高度境界の処理（Y）
								if (newPoint.chunkPosY + sideY < 0 || newPoint.chunkPosY + sideY >= WORLD_HEIGHT) {
									//境界からの減衰率把握
									float distance = getIntraChunkDistance(newPoint,centerX,centerY + sideY,centerZ);
									//高度外からの影響は減衰してない値を入れる
									side2_value = this.strength;
									map2_value = distance;
								} else {
									// 境界面ではないなら通常通りに
									side2_value = grid[newPoint.chunkPosX][newPoint.chunkPosY + sideY][newPoint.chunkPosZ];
									map2_value = gridMap[newPoint.chunkPosX][newPoint.chunkPosY + sideY][newPoint.chunkPosZ];
								}

								// 境界面と現在チャンクを分けて処理（X）
								if (newPoint.chunkPosX + sideX < 0) {
									// 境界面からデータ取得
									bg = this.bridgehMapW.get(chunk.z);
									side1_value = bg[0][newPoint.chunkPosZ][newPoint.chunkPosY];
									map1_value = bg[1][newPoint.chunkPosZ][newPoint.chunkPosY];
								} else if (newPoint.chunkPosX + sideX > 15) {
									// 境界面からデータ取得
									bg = this.bridgehMapE.get(chunk.z);
									side1_value = bg[0][newPoint.chunkPosZ][newPoint.chunkPosY];
									map1_value = bg[1][newPoint.chunkPosZ][newPoint.chunkPosY];
								} else {
									// 境界面ではないなら通常通りに
									side1_value = grid[newPoint.chunkPosX + sideX][newPoint.chunkPosY][newPoint.chunkPosZ];
									map1_value = gridMap[newPoint.chunkPosX + sideX][newPoint.chunkPosY][newPoint.chunkPosZ];
								}

								// 境界面と現在チャンクを分けて処理（Z）
								if (newPoint.chunkPosZ + sideZ < 0) {
									bg = this.bridgehMapN.get(chunk.x);
									side3_value = bg[0][newPoint.chunkPosX][newPoint.chunkPosY];
									map3_value = bg[1][newPoint.chunkPosX][newPoint.chunkPosY];
								} else if (newPoint.chunkPosZ + sideZ > 15) {
									bg = this.bridgehMapS.get(chunk.x);
									side3_value = bg[0][newPoint.chunkPosX][newPoint.chunkPosY];
									map3_value = bg[1][newPoint.chunkPosX][newPoint.chunkPosY];
								} else {
									// 境界面ではないなら通常通りに
									side3_value = grid[newPoint.chunkPosX][newPoint.chunkPosY][newPoint.chunkPosZ + sideZ];
									map3_value = gridMap[newPoint.chunkPosX][newPoint.chunkPosY][newPoint.chunkPosZ + sideZ];
								}


								// X, Y, Z の3軸全ての合計距離を計算する
								float totalDistance = Math.abs(directionX) + Math.abs(directionY) + Math.abs(directionZ);

								// 各軸の比率を計算する
								float horizontalRatio = Math.abs(directionX) / totalDistance;   // X軸の比率
								float verticalRatio = Math.abs(directionY) / totalDistance;     // Y軸の比率
								float depthRatio = Math.abs(directionZ) / totalDistance;        // Z軸の比率

								// 全ての軸の加重寄与に基づいて newValue を計算する
								newValue = (side1_value * horizontalRatio) +
									(side2_value * verticalRatio) +
									(side3_value * depthRatio);

								// 全ての軸の加重寄与に基づいて mapValue を計算する
								mapValue = (map1_value * horizontalRatio) +
									(map2_value * verticalRatio) +
									(map3_value * depthRatio);
							}
							// 中心のみ爆発開始量に変更する
							if(this.firstSteps) {
								newValue = this.strength;
								mapValue = 0;
								ran = 0.5F;
								this.firstSteps = false;
							}

							//減衰率把握
							float distance = getIntraChunkDistance(newPoint,centerX,centerY,centerZ); // チャンク内の詳細距離
							float attenuation = distance - mapValue; // new_pointから引く量を計算

							Block block = Blocks.air;
							try {
								// 爆破耐性考慮
								block = this.world.getBlock(worldX, worldY, worldZ);

								double fac = (1 - ((double) distance) / ((double) this.strength)) * 7.0D;
								attenuation *= ran * 1.9F + 0.05F;

								if (!block.getMaterial().isLiquid()) {
									double d = masqueradeResistance(block);
									if (d < MAX_RESISTANCE)
										newValue -= (float) Math.pow(d, 7.5D - fac) * attenuation;
									else
										// ran次第でたまに岩盤を貫通するので岩盤等は強制的に止める
										newValue = 0;
								}
							} catch (Exception e) {
//								System.out.println("BlockeError["+worldX+", "+worldY+", "+worldZ+"]");
							}

							// 淵をランダムに
							if (distance > this.length -1 && (distance > this.length || ran < 0.5))
								newValue = 0;

							if (newValue <= 0) {
								// 未使用のセルかどうかの判断は0かそれ以外で判断しているためこのような処理がある
								newValue = 0.00000000001f;
							}else if (block != Blocks.air) {
								blockMap[newPoint.chunkPosX][newPoint.chunkPosY][newPoint.chunkPosZ] = true;
							}

							// 追加処理
							setGridData(grid,gridMap,bridgeGrid,newPoint.chunkPosX,newPoint.chunkPosY,newPoint.chunkPosZ,newValue,distance);
							newPoints.add(newPoint);
						}
					}
				}
			}
			expandPoints = newPoints;
		}

		// チャンク境界面マップを更新
		// 正負でその方向、0だったら両側
		if(chunk.x >= 0){
			this.bridgehMapW.put(chunk.z,bridgeGrid[1]);
		}
		if(chunk.x <= 0){
			this.bridgehMapE.put(chunk.z,bridgeGrid[0]);
		}
		if(chunk.z >= 0){
			this.bridgehMapN.put(chunk.x,bridgeGrid[3]);
		}
		if(chunk.z <= 0){
			this.bridgehMapS.put(chunk.x,bridgeGrid[2]);
		}


		// ブロック除去
		boolean check1, check2; // block更新フラグ
		for (int i = 0; i < 16; i++) {
			for (int k = 0; k < 16; k++) {
				for (int j = 0; j < WORLD_HEIGHT; j++) {
					if (blockMap[i][j][k]) {

						check1 = true;
						check2 = false;
						float f = grid[i][j][k];
						if (f > BORDER) {
							int x = i - centerX;
							int y = j - centerY;
							int z = k - centerZ;

							if (i == 15 && chunk.x >= 0)
								check1 = false;
							if (i == 0 && chunk.x <= 0)
								check1 = false;
							if (k == 15 && chunk.z >= 0)
								check1 = false;
							if (k == 0 && chunk.z <= 0)
								check1 = false;

							if(check1){
								// 何かしらのブロックに隣接しているかcheck
								if (0 < j && grid[i][j-1][k] <= BORDER)
									check2 = true;
								if (!check2 && (WORLD_HEIGHT-1) > j && grid[i][j+1][k] <= BORDER)
									check2 = true;
								if (!check2 && 0 < i && grid[i-1][j][k] <= BORDER)
									check2 = true;
								if (!check2 && 15 > i && grid[i+1][j][k] <= BORDER)
									check2 = true;
								if (!check2 && 0 < k && grid[i][j][k-1] <= BORDER)
									check2 = true;
								if (!check2 && 15 > k && grid[i][j][k+1] <= BORDER)
									check2 = true;
							}

							if(check2)
								world.setBlock(x + this.placeX, y + this.placeY, z + this.placeZ, Blocks.air, 0, 3);
							else
								world.setBlock(x + this.placeX, y + this.placeY, z + this.placeZ, Blocks.air, 0, 2);
						}
					}
				}
			}
		}
	}
	public static float masqueradeResistance(Block block) {

		if(block == Blocks.sandstone) return Blocks.stone.getExplosionResistance(null);
		if(block == Blocks.obsidian) return Blocks.stone.getExplosionResistance(null) * 3;
		return block.getExplosionResistance(null);
	}

	// グリッドに値を入れる際の処理、境界面判定も行う
	private static void setGridData(float[][][] grid,float[][][] gridMap,float[][][][] bridgeGrid,int x,int y,int z,float newValue,float spread){
		grid[x][y][z] = newValue;
		gridMap[x][y][z] = spread;

		// 境界面保存処理
		if(x == 0){
			bridgeGrid[0][0][z][y] = newValue;
			bridgeGrid[0][1][z][y] = spread;
		}else if(x == 15){
			bridgeGrid[1][0][z][y] = newValue;
			bridgeGrid[1][1][z][y] = spread;
		}
		if(z == 0){
			bridgeGrid[2][0][x][y] = newValue;
			bridgeGrid[2][1][x][y] = spread;
		}else if(z == 15){
			bridgeGrid[3][0][x][y] = newValue;
			bridgeGrid[3][1][x][y] = spread;
		}
	}

	//　チャンク内からの距離詳細
	protected float getIntraChunkDistance(ChunkPosition point, int centerX, int centerY, int centerZ){
		double position =
			this.squared(point.chunkPosX - centerX) +
			this.squared(point.chunkPosY - centerY) +
			this.squared(point.chunkPosZ - centerZ);
		return (float) sqrt(position);
	}
	protected float squared(int n){
		return n * n;
	}

	protected ChunkPosition[] getPeripheral(ChunkPosition cp) {
		int x = cp.chunkPosX;
		int y = cp.chunkPosY;
		int z = cp.chunkPosZ;
		return new ChunkPosition[]{
			cp,
			new ChunkPosition(x + 1, y, z),
			new ChunkPosition(x - 1, y, z),
			new ChunkPosition(x, y + 1, z),
			new ChunkPosition(x, y - 1, z),
			new ChunkPosition(x, y, z + 1),
			new ChunkPosition(x, y, z - 1)
		};
	}

	@Override
	public void cacheChunksTick(int processTimeMs) {
		//特になし
	}

	@Override
	public void destructionTick(int processTimeMs) {

		// 開始時間を記録
		long startTime = System.currentTimeMillis();

		while (true) {
			if(currentLayer == null || !currentLayer.hasNext()) {
				// 近いマスを取得＆削除
				int k = scheduleChunks.firstKey();
				Chunk point = pollFirstEntry(k);
				// 完了したらフラグ立て
				if (k > this.maxDistanceChunks) {
					this.complete = true;
					return;
				}

				int x = point.x;
				int z = point.z;

				// 新しいマス
				if (z == 0) {
					this.addCircleMap(x + 1, 0);
				}
				if (x > z) {
					this.addCircleMap(x, z + 1);
				}

				HashSet<Chunk> layer = new HashSet<Chunk>();
				layer.add(new Chunk(point.x,point.z));
				layer.add(new Chunk(point.x,-point.z));
				layer.add(new Chunk(-point.x,point.z));
				layer.add(new Chunk(-point.x,-point.z));
				layer.add(new Chunk(point.z,point.x));
				layer.add(new Chunk(point.z,-point.x));
				layer.add(new Chunk(-point.z,point.x));
				layer.add(new Chunk(-point.z,-point.x));
				currentLayer = layer.iterator();
			}
			// チャンクの消去処理を実行
			chunkGrid(currentLayer.next());

			// ある程度時間が足ったら強制中断
			if ((System.currentTimeMillis() - startTime) > processTimeMs) return;
		}
	}
	// 追加
	private void addCircleMap(int x,int z){
		int key = x*x+z*z;
		if (!scheduleChunks.containsKey(key)) {
			scheduleChunks.put(key, new ArrayList<>());
		}
		scheduleChunks.get(key).add(new Chunk(x, z));
	}
	// 取得&削除
	private Chunk pollFirstEntry(int key){
		int in = scheduleChunks.get(key).size() - 1;
//		System.out.print("size: " + scheduleChunks.size() + ", " + scheduleChunks.get(key).size() + ", ");
		Chunk c = scheduleChunks.get(key).get(in);
//		System.out.println(c);
		scheduleChunks.get(key).remove(in);
		if(scheduleChunks.get(key).isEmpty()){
			scheduleChunks.remove(key);
		}
		return c;
	}

	@Override
	public void cancel() {

	}

	public boolean isComplete() {
		return this.complete;
	}

	// チャンクのデータとか
	static class Chunk {
		int x, z;
		public Chunk(int x, int z) {
			this.x = x;
			this.z = z;
		}
		@Override
		public boolean equals(Object obj) {
			if (this == obj) return true;
			if (obj == null || getClass() != obj.getClass()) return false;
			Chunk chunk = (Chunk) obj;
			return x == chunk.x && z == chunk.z;
		}

		@Override
		public int hashCode() {
			return 31 * x + z;
		}

		@Override
		public String toString() {
			return "Chunk[" + x + ", " + z + "]";
		}
	}
}
