package com.codingame.code4life;

import com.codingame.common.MultiReferee;

import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class Referee extends MultiReferee {

	public static int LEAGUE_LEVEL = 0; // 0, 1 or 2

	public static final int EV_NEW_SAMPLE = 0;
	public static final int EV_TAKE_SAMPLE = 1;
	public static final int EV_STORE_SAMPLE = 2;
	public static final int EV_TAKE_RESOURCE = 3;
	public static final int EV_DIAGNOSE = 4;
	public static final int EV_PRODUCE = 5;
	public static final int EV_CLONE_INITAL_SAMPLE = 6;
	public static final int[] RESOURCE_PER_TYPE_BY_LEAGUE_LEVEL = { 99, 99, 6 };
	public static final int[] SCIENCE_PROJECTS_BY_LEAGUE_LEVEL = { 0, 0, 3 };
	public static final int[] INIT_DIAGNOSED_SAMPLES_BY_LEAGUE_LEVEL = { 50, 0, 0 };
	public static final int MAX_STORAGE = 10;
	public static final int MAX_TRAY = 3;
	public static final int SAMPLE_RANK_COUNT = 3;
	public static final int SCIENCE_PROJECT_VALUE = 30;
	public static final int MAX_SCORE = 170;

	static final Pattern PLAYER_MOVE_PATTERN = Pattern.compile("^GOTO\\s+(?<module>LABORATORY|DIAGNOSIS|MOLECULES|SAMPLES)(?:\\s+)?(?:\\s+(?<message>.+))?", Pattern.CASE_INSENSITIVE);
	static final Pattern PLAYER_WAIT_PATTERN = Pattern.compile("^WAIT(?:\\s+)?(?:\\s+(?<message>.+))?", Pattern.CASE_INSENSITIVE);
	static final Pattern PLAYER_USE_PATTERN = Pattern.compile("^CONNECT(?:\\s+(?<data>[ABCDE]|(?:-?\\d+)))?(?:\\s+)?(?:\\s+(?<message>.+))?$", Pattern.CASE_INSENSITIVE);
	static final String[] EXPECTED_BY_LEAGUE_LEVEL = { "GOTO LABORATORY|DIAGNOSIS|MOLECULES | CONNECT data", "GOTO LABORATORY|DIAGNOSIS|MOLECULES|SAMPLES | CONNECT data", "GOTO LABORATORY|DIAGNOSIS|MOLECULES|SAMPLES | CONNECT data" };

	private List<PlayerData> players;
	private List<Transfer> transfers;
	private Map<Sample, SampleTransfer> cloudRequests;
	private List<Diagnosis> diagnoses;
	private List<ProjectCompletion> projectCompletions;
	private Random random;
	private List<LinkedList<Sample>> samplePool;
	List<Sample> storedSamples;
	List<ScienceProject> scienceProjects;
	private long seed;
	Map<MoleculeType, Integer> molecules;
	Map<ModulePair, Integer> distances;

	public Referee(InputStream is, PrintStream out, PrintStream err) throws IOException {
		super(is, out, err);
	}

	@Override
	protected void initReferee(int playerCount, Properties prop) throws InvalidFormatException {
		seed = Long.valueOf(prop.getProperty("seed", String.valueOf(new Random(System.currentTimeMillis()).nextLong())));

		random = new Random(seed);

		// map
		initMap();

		// players
		players = new ArrayList<PlayerData>();
		for (int i = 0; i < playerCount; ++i) {
			players.add(i, new PlayerData(i));
		}

		// resources
		molecules = new HashMap<>();
		for (MoleculeType type : MoleculeType.values()) {
			molecules.put(type, RESOURCE_PER_TYPE_BY_LEAGUE_LEVEL[LEAGUE_LEVEL]);
		}

		// samples
		initSamplePool();
		storedSamples = new LinkedList<>();

		// science
		initScienceProjects();

		// diagnosis
		initDiagnonisModule();

		transfers = new LinkedList<>();
		cloudRequests = new HashMap<>();
		diagnoses = new LinkedList<>();
		projectCompletions = new LinkedList<>();

	}

	private void initScienceProjects() {
		LinkedList<ScienceProject> scienceProjectPool;
		scienceProjectPool = new LinkedList<>();
		scienceProjectPool.add(new ScienceProject(new int[] { 3, 3, 0, 0, 3 }));
		scienceProjectPool.add(new ScienceProject(new int[] { 0, 3, 3, 3, 0 }));
		scienceProjectPool.add(new ScienceProject(new int[] { 3, 0, 0, 3, 3 }));
		scienceProjectPool.add(new ScienceProject(new int[] { 0, 0, 4, 4, 0 }));
		scienceProjectPool.add(new ScienceProject(new int[] { 0, 4, 4, 0, 0 }));
		scienceProjectPool.add(new ScienceProject(new int[] { 0, 0, 0, 4, 4 }));
		scienceProjectPool.add(new ScienceProject(new int[] { 4, 0, 0, 0, 4 }));
		scienceProjectPool.add(new ScienceProject(new int[] { 3, 3, 3, 0, 0 }));
		scienceProjectPool.add(new ScienceProject(new int[] { 0, 0, 3, 3, 3 }));
		scienceProjectPool.add(new ScienceProject(new int[] { 4, 4, 0, 0, 0 }));
		Collections.shuffle(scienceProjectPool, random);

		scienceProjects = new ArrayList<>(SCIENCE_PROJECTS_BY_LEAGUE_LEVEL[LEAGUE_LEVEL]);
		for (int i = 0; i < SCIENCE_PROJECTS_BY_LEAGUE_LEVEL[LEAGUE_LEVEL]; ++i) {
			ScienceProject project = scienceProjectPool.pop();
			project.index = i;
			scienceProjects.add(project);
		}
	}

	private void initSamplePool() {
		samplePool = new ArrayList<LinkedList<Sample>>(SAMPLE_RANK_COUNT);
		for (int rank = 0; rank < SAMPLE_RANK_COUNT; ++rank) {
			LinkedList<Sample> cells = new LinkedList<Sample>();
			samplePool.add(cells);
		}
		samplePool.get(0).add(new Sample(new int[] { 0, 3, 0, 0, 0 }, 01, MoleculeType.A));
		samplePool.get(0).add(new Sample(new int[] { 0, 0, 0, 2, 1 }, 01, MoleculeType.A));
		samplePool.get(0).add(new Sample(new int[] { 0, 1, 1, 1, 1 }, 01, MoleculeType.A));
		samplePool.get(0).add(new Sample(new int[] { 0, 2, 0, 0, 2 }, 01, MoleculeType.A));
		samplePool.get(0).add(new Sample(new int[] { 0, 0, 4, 0, 0 }, 10, MoleculeType.A));
		samplePool.get(0).add(new Sample(new int[] { 0, 1, 2, 1, 1 }, 01, MoleculeType.A));
		samplePool.get(0).add(new Sample(new int[] { 0, 2, 2, 0, 1 }, 01, MoleculeType.A));
		samplePool.get(0).add(new Sample(new int[] { 3, 1, 0, 0, 1 }, 01, MoleculeType.A));
		samplePool.get(0).add(new Sample(new int[] { 1, 0, 0, 0, 2 }, 01, MoleculeType.B));
		samplePool.get(0).add(new Sample(new int[] { 0, 0, 0, 0, 3 }, 01, MoleculeType.B));
		samplePool.get(0).add(new Sample(new int[] { 1, 0, 1, 1, 1 }, 01, MoleculeType.B));
		samplePool.get(0).add(new Sample(new int[] { 0, 0, 2, 0, 2 }, 01, MoleculeType.B));
		samplePool.get(0).add(new Sample(new int[] { 0, 0, 0, 4, 0 }, 10, MoleculeType.B));
		samplePool.get(0).add(new Sample(new int[] { 1, 0, 1, 2, 1 }, 01, MoleculeType.B));
		samplePool.get(0).add(new Sample(new int[] { 1, 0, 2, 2, 0 }, 01, MoleculeType.B));
		samplePool.get(0).add(new Sample(new int[] { 0, 1, 3, 1, 0 }, 01, MoleculeType.B));
		samplePool.get(0).add(new Sample(new int[] { 2, 1, 0, 0, 0 }, 01, MoleculeType.C));
		samplePool.get(0).add(new Sample(new int[] { 0, 0, 0, 3, 0 }, 01, MoleculeType.C));
		samplePool.get(0).add(new Sample(new int[] { 1, 1, 0, 1, 1 }, 01, MoleculeType.C));
		samplePool.get(0).add(new Sample(new int[] { 0, 2, 0, 2, 0 }, 01, MoleculeType.C));
		samplePool.get(0).add(new Sample(new int[] { 0, 0, 0, 0, 4 }, 10, MoleculeType.C));
		samplePool.get(0).add(new Sample(new int[] { 1, 1, 0, 1, 2 }, 01, MoleculeType.C));
		samplePool.get(0).add(new Sample(new int[] { 0, 1, 0, 2, 2 }, 01, MoleculeType.C));
		samplePool.get(0).add(new Sample(new int[] { 1, 3, 1, 0, 0 }, 01, MoleculeType.C));
		samplePool.get(0).add(new Sample(new int[] { 0, 2, 1, 0, 0 }, 01, MoleculeType.D));
		samplePool.get(0).add(new Sample(new int[] { 3, 0, 0, 0, 0 }, 01, MoleculeType.D));
		samplePool.get(0).add(new Sample(new int[] { 1, 1, 1, 0, 1 }, 01, MoleculeType.D));
		samplePool.get(0).add(new Sample(new int[] { 2, 0, 0, 2, 0 }, 01, MoleculeType.D));
		samplePool.get(0).add(new Sample(new int[] { 4, 0, 0, 0, 0 }, 10, MoleculeType.D));
		samplePool.get(0).add(new Sample(new int[] { 2, 1, 1, 0, 1 }, 01, MoleculeType.D));
		samplePool.get(0).add(new Sample(new int[] { 2, 0, 1, 0, 2 }, 01, MoleculeType.D));
		samplePool.get(0).add(new Sample(new int[] { 1, 0, 0, 1, 3 }, 01, MoleculeType.D));
		samplePool.get(0).add(new Sample(new int[] { 0, 0, 2, 1, 0 }, 01, MoleculeType.E));
		samplePool.get(0).add(new Sample(new int[] { 0, 0, 3, 0, 0 }, 01, MoleculeType.E));
		samplePool.get(0).add(new Sample(new int[] { 1, 1, 1, 1, 0 }, 01, MoleculeType.E));
		samplePool.get(0).add(new Sample(new int[] { 2, 0, 2, 0, 0 }, 01, MoleculeType.E));
		samplePool.get(0).add(new Sample(new int[] { 0, 4, 0, 0, 0 }, 10, MoleculeType.E));
		samplePool.get(0).add(new Sample(new int[] { 1, 2, 1, 1, 0 }, 01, MoleculeType.E));
		samplePool.get(0).add(new Sample(new int[] { 2, 2, 0, 1, 0 }, 01, MoleculeType.E));
		samplePool.get(0).add(new Sample(new int[] { 0, 0, 1, 3, 1 }, 01, MoleculeType.E));
		samplePool.get(1).add(new Sample(new int[] { 0, 0, 0, 5, 0 }, 20, MoleculeType.A));
		samplePool.get(1).add(new Sample(new int[] { 6, 0, 0, 0, 0 }, 30, MoleculeType.A));
		samplePool.get(1).add(new Sample(new int[] { 0, 0, 3, 2, 2 }, 10, MoleculeType.A));
		samplePool.get(1).add(new Sample(new int[] { 0, 0, 1, 4, 2 }, 20, MoleculeType.A));
		samplePool.get(1).add(new Sample(new int[] { 2, 3, 0, 3, 0 }, 10, MoleculeType.A));
		samplePool.get(1).add(new Sample(new int[] { 0, 0, 0, 5, 3 }, 20, MoleculeType.A));
		samplePool.get(1).add(new Sample(new int[] { 0, 5, 0, 0, 0 }, 20, MoleculeType.B));
		samplePool.get(1).add(new Sample(new int[] { 0, 6, 0, 0, 0 }, 30, MoleculeType.B));
		samplePool.get(1).add(new Sample(new int[] { 0, 2, 2, 3, 0 }, 10, MoleculeType.B));
		samplePool.get(1).add(new Sample(new int[] { 2, 0, 0, 1, 4 }, 20, MoleculeType.B));
		samplePool.get(1).add(new Sample(new int[] { 5, 3, 0, 0, 0 }, 20, MoleculeType.B));
		samplePool.get(1).add(new Sample(new int[] { 0, 0, 5, 0, 0 }, 20, MoleculeType.C));
		samplePool.get(1).add(new Sample(new int[] { 0, 0, 6, 0, 0 }, 30, MoleculeType.C));
		samplePool.get(1).add(new Sample(new int[] { 2, 3, 0, 0, 2 }, 10, MoleculeType.C));
		samplePool.get(1).add(new Sample(new int[] { 3, 0, 2, 2, 0 }, 10, MoleculeType.C));
		samplePool.get(1).add(new Sample(new int[] { 4, 2, 0, 0, 1 }, 20, MoleculeType.C));
		samplePool.get(1).add(new Sample(new int[] { 0, 5, 3, 0, 0 }, 20, MoleculeType.C));
		samplePool.get(1).add(new Sample(new int[] { 0, 0, 0, 0, 5 }, 20, MoleculeType.D));
		samplePool.get(1).add(new Sample(new int[] { 0, 0, 0, 6, 0 }, 30, MoleculeType.D));
		samplePool.get(1).add(new Sample(new int[] { 2, 0, 0, 2, 3 }, 10, MoleculeType.D));
		samplePool.get(1).add(new Sample(new int[] { 1, 4, 2, 0, 0 }, 20, MoleculeType.D));
		samplePool.get(1).add(new Sample(new int[] { 0, 3, 0, 2, 3 }, 10, MoleculeType.D));
		samplePool.get(1).add(new Sample(new int[] { 3, 0, 0, 0, 5 }, 20, MoleculeType.D));
		samplePool.get(1).add(new Sample(new int[] { 0, 0, 0, 0, 5 }, 20, MoleculeType.E));
		samplePool.get(1).add(new Sample(new int[] { 0, 0, 0, 0, 6 }, 30, MoleculeType.E));
		samplePool.get(1).add(new Sample(new int[] { 3, 2, 2, 0, 0 }, 10, MoleculeType.E));
		samplePool.get(1).add(new Sample(new int[] { 0, 1, 4, 2, 0 }, 20, MoleculeType.E));
		samplePool.get(1).add(new Sample(new int[] { 3, 0, 3, 0, 2 }, 10, MoleculeType.E));
		samplePool.get(1).add(new Sample(new int[] { 0, 0, 5, 3, 0 }, 20, MoleculeType.E));
		samplePool.get(2).add(new Sample(new int[] { 0, 0, 0, 0, 7 }, 40, MoleculeType.A));
		samplePool.get(2).add(new Sample(new int[] { 3, 0, 0, 0, 7 }, 50, MoleculeType.A));
		samplePool.get(2).add(new Sample(new int[] { 3, 0, 0, 3, 6 }, 40, MoleculeType.A));
		samplePool.get(2).add(new Sample(new int[] { 0, 3, 3, 5, 3 }, 30, MoleculeType.A));
		samplePool.get(2).add(new Sample(new int[] { 7, 0, 0, 0, 0 }, 40, MoleculeType.B));
		samplePool.get(2).add(new Sample(new int[] { 7, 3, 0, 0, 0 }, 50, MoleculeType.B));
		samplePool.get(2).add(new Sample(new int[] { 6, 3, 0, 0, 3 }, 40, MoleculeType.B));
		samplePool.get(2).add(new Sample(new int[] { 3, 0, 3, 3, 5 }, 30, MoleculeType.B));
		samplePool.get(2).add(new Sample(new int[] { 0, 7, 0, 0, 0 }, 40, MoleculeType.C));
		samplePool.get(2).add(new Sample(new int[] { 0, 7, 3, 0, 0 }, 50, MoleculeType.C));
		samplePool.get(2).add(new Sample(new int[] { 3, 6, 3, 0, 0 }, 40, MoleculeType.C));
		samplePool.get(2).add(new Sample(new int[] { 5, 3, 0, 3, 3 }, 30, MoleculeType.C));
		samplePool.get(2).add(new Sample(new int[] { 0, 0, 7, 0, 0 }, 40, MoleculeType.D));
		samplePool.get(2).add(new Sample(new int[] { 0, 0, 7, 3, 0 }, 50, MoleculeType.D));
		samplePool.get(2).add(new Sample(new int[] { 0, 3, 6, 3, 0 }, 40, MoleculeType.D));
		samplePool.get(2).add(new Sample(new int[] { 3, 5, 3, 0, 3 }, 30, MoleculeType.D));
		samplePool.get(2).add(new Sample(new int[] { 0, 0, 0, 7, 0 }, 40, MoleculeType.E));
		samplePool.get(2).add(new Sample(new int[] { 0, 0, 0, 7, 3 }, 50, MoleculeType.E));
		samplePool.get(2).add(new Sample(new int[] { 0, 0, 3, 6, 3 }, 40, MoleculeType.E));
		samplePool.get(2).add(new Sample(new int[] { 3, 3, 5, 3, 0 }, 30, MoleculeType.E));

		for (int rank = 0; rank < SAMPLE_RANK_COUNT; ++rank) {
			Collections.shuffle(samplePool.get(rank), random);
		}

	}

	private void initDiagnonisModule() {
		for (int i = 0; i < INIT_DIAGNOSED_SAMPLES_BY_LEAGUE_LEVEL[LEAGUE_LEVEL]; i++) {
			int rank = 0;
			Sample sample = samplePool.get(rank).pop();
			samplePool.get(rank).add(sample.clone());

			sample.id = Sample.ENTITY_COUNT++;
			sample.rank = rank;
			sample.setDiscovered(true);
			if (LEAGUE_LEVEL <= 1) {
				sample.expertise = null;
			}
			storedSamples.add(sample);
		}
	}

	private void initMap() {
		distances = new HashMap<>();
		if (LEAGUE_LEVEL >= 2) {
			distances.put(new ModulePair(Module.START_POS, Module.SAMPLES), 2);
			distances.put(new ModulePair(Module.START_POS, Module.DIAGNOSIS), 2);
			distances.put(new ModulePair(Module.START_POS, Module.MOLECULES), 2);
			distances.put(new ModulePair(Module.START_POS, Module.LABORATORY), 2);
			distances.put(new ModulePair(Module.SAMPLES, Module.DIAGNOSIS), 3);
			distances.put(new ModulePair(Module.SAMPLES, Module.MOLECULES), 3);
			distances.put(new ModulePair(Module.SAMPLES, Module.LABORATORY), 3);
			distances.put(new ModulePair(Module.DIAGNOSIS, Module.MOLECULES), 3);
			distances.put(new ModulePair(Module.DIAGNOSIS, Module.LABORATORY), 4);
			distances.put(new ModulePair(Module.MOLECULES, Module.LABORATORY), 3);
		} else {
			distances.put(new ModulePair(Module.START_POS, Module.SAMPLES), 1);
			distances.put(new ModulePair(Module.START_POS, Module.DIAGNOSIS), 1);
			distances.put(new ModulePair(Module.START_POS, Module.MOLECULES), 1);
			distances.put(new ModulePair(Module.START_POS, Module.LABORATORY), 1);
			distances.put(new ModulePair(Module.SAMPLES, Module.DIAGNOSIS), 1);
			distances.put(new ModulePair(Module.SAMPLES, Module.MOLECULES), 1);
			distances.put(new ModulePair(Module.SAMPLES, Module.LABORATORY), 1);
			distances.put(new ModulePair(Module.DIAGNOSIS, Module.MOLECULES), 1);
			distances.put(new ModulePair(Module.DIAGNOSIS, Module.LABORATORY), 1);
			distances.put(new ModulePair(Module.MOLECULES, Module.LABORATORY), 1);
		}
	}

	@Override
	protected Properties getConfiguration() {
		Properties prop = new Properties();
		prop.setProperty("seed", String.valueOf(seed));
		return prop;
	}

	@Override
	protected String[] getInitInputForPlayer(int playerIdx) {
		List<String> lines = new ArrayList<>();
		lines.add(String.valueOf(scienceProjects.size()));
		for (ScienceProject project : scienceProjects) {
			lines.add(resourceArrayToString(project.cost));
		}

		return lines.toArray(new String[lines.size()]);
	}

	@Override
	protected void prepare(int round) {
		transfers.clear();
		diagnoses.clear();
		cloudRequests.clear();
		projectCompletions.clear();
		for (PlayerData player : players) {
			player.reset();
		}
	}

	@Override
	protected String[] getInputForPlayer(int round, int playerIdx) {
		List<String> lines = new ArrayList<>();
		List<String> sampleLines = new ArrayList<>();

		Stream<PlayerData> a = players.stream().filter(p -> (p.index == playerIdx));
		Stream<PlayerData> b = players.stream().filter(p -> (p.index != playerIdx));
		List<PlayerData> reordered = Stream.concat(a, b).collect(Collectors.toList());
		reordered.stream().forEachOrdered(player -> {
			StringJoiner sj = new StringJoiner(" ");
			sj.add(player.target.name());
			sj.add(String.valueOf(player.eta));
			sj.add(String.valueOf(player.score));
			sj.add(resourceArrayToString(player.storage));
			sj.add(resourceArrayToString(player.expertise));

			for (Sample sample : player.tray) {
				int carrier = player.index == playerIdx ? 0 : 1;
				if (sample.isDiscovered()) {
					sampleLines.add(join(sample.id, carrier, sample.rank + 1, sample.getGainChar(), sample.life, resourceArrayToString(sample.cost)));
				} else {
					sampleLines.add(join(sample.id, carrier, sample.rank + 1, "0 -1 -1 -1 -1 -1 -1"));
				}
			}
			lines.add(sj.toString());
		});

		for (Sample sample : storedSamples) {
			sampleLines.add(join(sample.id, -1, sample.rank, sample.getGainChar(), sample.life, resourceArrayToString(sample.cost)));
		}

		lines.add(Arrays.stream(MoleculeType.values()).map(type -> String.valueOf(Math.max(0, molecules.get(type)))).collect(Collectors.joining(" ")));
		lines.add(String.valueOf(sampleLines.size()));
		lines.addAll(sampleLines);

		return lines.toArray(new String[lines.size()]);

	}

	@Override
	protected int getExpectedOutputLineCountForPlayer(int playerIdx) {
		return 1;
	}

	@Override
	protected void handlePlayerOutput(int frame, int round, int playerIdx, String[] outputs) throws WinException, LostException, InvalidInputException {
		String line = outputs[0];
		PlayerData player = players.get(playerIdx);

		try {
			if (player.isMoving()) {
				player.setMessage(line);
				return;
			}

			Matcher match = PLAYER_MOVE_PATTERN.matcher(line);
			if (match.matches()) {
				// Movement
				String module = match.group("module");

				Module target = Module.valueOf(module.toUpperCase());
				if (target == Module.SAMPLES && LEAGUE_LEVEL == 0) {
					throw new InvalidInputException(EXPECTED_BY_LEAGUE_LEVEL[LEAGUE_LEVEL], line);
				}

				if (player.target != target) {
					player.from = player.target;
					player.target = target;
					player.eta = distances.get(new ModulePair(player.target, player.from));
				}

				// Message
				matchMessage(player, match);
				return;
			}

			match = PLAYER_USE_PATTERN.matcher(line);
			if (match.matches()) {
				// Connect to machine
				String data = match.group("data");
				player.attemptConnection = true;
				player.connectionData = data;

				connectToMachine(player, data);

				// Message
				matchMessage(player, match);
				return;
			}

			match = PLAYER_WAIT_PATTERN.matcher(line);
			if (match.matches()) {
				// Message
				matchMessage(player, match);
				return;
			}

			throw new InvalidInputException(EXPECTED_BY_LEAGUE_LEVEL[LEAGUE_LEVEL], line);

		} catch (LostException | InvalidInputException e) {
			player.die(round);
			throw e;
		} catch (Exception e) {
			player.die(round);
			throw new InvalidInputException(EXPECTED_BY_LEAGUE_LEVEL[LEAGUE_LEVEL], line);
		}
	}

	private void connectToMachine(PlayerData player, String data) throws LostException {
		try {
			switch (player.target) {
			case SAMPLES:
				try {
					requestSample(player, Integer.valueOf(data));
				} catch (NumberFormatException e) {
					throw new LostException("badSampleRank", data);
				}
				break;
			case MOLECULES:
				MoleculeType molecule;
				try {
					molecule = MoleculeType.valueOf(data.toUpperCase());
				} catch (Exception e) {
					throw new LostException("unknownMoleculeType", data != null ? data : "");
				}
				requestMolecule(player, molecule);
				break;
			case DIAGNOSIS:
				requestDiagnosis(player, Integer.valueOf(data));
				break;
			case LABORATORY:
				requestProduction(player, Integer.valueOf(data));
				break;
			case START_POS:
				throw new LostException("connectToNothing");
			default:
				break;
			}
		} catch (LostException le) {
			le.setTooltipCode("InvalidConnect");
			throw le;
		}
	}

	private void requestProduction(PlayerData player, Integer data) throws LostException {
		if (data == null) {
			throw new LostException("nullIsInvalidSample");
		}

		Optional<Sample> target = player.tray.stream().filter(sample -> data.equals(sample.id)).findFirst();
		if (target.isPresent()) {
			Sample sample = target.get();
			if (canAfford(player, sample.cost)) {
				transfers.add(new ProductionTransfer(player, sample));
				return;
			}
			throw new LostException("cannotAffordSample", data);
		}
		throw new LostException("sampleNotInTray", data);
	}

	private boolean canAfford(PlayerData player, int[] cost) {
		for (int i = 0; i < MoleculeType.values().length; ++i) {
			if (player.expertise[i] + player.storage[i] < cost[i]) {
				return false;
			}
		}
		return true;
	}

	private void requestDiagnosis(PlayerData player, Integer data) throws LostException {
		if (data == null) {
			throw new LostException("nullIsInvalidSample");
		}

		Optional<Sample> target = player.tray.stream().filter(sample -> data.equals(sample.id)).findFirst();

		if (target.isPresent()) {
			Sample sample = target.get();
			if (sample.isDiscovered()) {
				transfers.add(new SampleTransfer(player, sample, Bound.TO_DIAGNOSIS));
				return;
			} else if (!sample.isDiscovered()) {
				// Diagnose
				diagnoses.add(new Diagnosis(player, sample));
				sample.setDiscovered(true);
				sample.discoveredBy = player;
				return;
			}
		} else {
			target = storedSamples.stream().filter(sample -> data.equals(sample.id)).findFirst();
			if (!target.isPresent()) {
				throw new LostException("sampleNotFound", data);
			}
			if (player.tray.size() >= MAX_TRAY) {
				throw new LostException("trayIsFull");
			}
			Sample sample = target.get();
			SampleTransfer transfer = new SampleTransfer(player, sample, Bound.FROM_DIAGNOSIS);

			if (cloudRequests.get(sample) == null || transfer.player == sample.discoveredBy) {
				cloudRequests.put(sample, transfer);
			} else if (LEAGUE_LEVEL == 0) {
				Sample clonedSample = sample.clone();
				clonedSample.id = Sample.ENTITY_COUNT++;
				clonedSample.setDiscovered(true);
				transfer.setClone(clonedSample);
				cloudRequests.put(clonedSample, transfer);
			}
		}

	}

	private void requestMolecule(PlayerData player, MoleculeType type) throws LostException {
		if (molecules.get(type) <= 0) {
			throw new LostException("notEnoughMolecules", type.name());
		}
		if (Arrays.stream(player.storage).sum() >= MAX_STORAGE) {
			throw new LostException("storageIsFull");
		}
		transfers.add(new ResourceTransfer(player, type));
	}

	private void requestSample(PlayerData player, int rank) throws LostException {
		if (player.tray.size() >= MAX_TRAY) {
			throw new LostException("trayIsFull");
		}

		if (rank < 1 || rank > 3) {
			// throw new LostException("badSampleRank", String.valueOf(rank));
		}

		Sample sample = samplePool.get(rank - 1).pop();
		// Just recycle it right back in there.
		samplePool.get(rank - 1).add(sample.clone());

		sample.id = Sample.ENTITY_COUNT++;
		sample.rank = rank - 1;
		sample.setDiscovered(false);

		if (LEAGUE_LEVEL <= 1) {
			sample.expertise = null;
		}

		transfers.add(new SampleTransfer(player, sample, Bound.FROM_SAMPLES));

	}

	private void matchMessage(PlayerData player, Matcher match) {
		player.setMessage(match.group("message"));
	}

	public double distance(Point a, Point b) {
		return Math.sqrt(Math.pow(b.x - a.x, 2) + Math.pow(b.y - a.y, 2));
	}

	@Override
	protected void updateGame(int round) throws GameOverException {
		// Move players
		for (PlayerData player : players) {
			if (player.eta != 0) {
				player.eta--;
				player.moved = true;
			}
		}

		// Perform transfers
		for (SampleTransfer transfer : cloudRequests.values()) {
			transfers.add(transfer);
		}
		for (Transfer transfer : transfers) {
			transfer.apply(this);
		}

		// Check for science projects
		List<Runnable> removes = new LinkedList<>();
		for (PlayerData player : players) {
			for (ScienceProject project : scienceProjects) {
				if (completedProject(player, project)) {
					removes.add(() -> {
						scienceProjects.remove(project);
					});
					player.score += SCIENCE_PROJECT_VALUE;
					projectCompletions.add(new ProjectCompletion(player, project));
					addToolTip(player.index, translate("ProjectTooltip", player.index));
				}
			}

		}
		for (Runnable r : removes) {
			r.run();
		}

	}

	private boolean completedProject(PlayerData player, ScienceProject project) {
		for (int i = 0; i < project.cost.length; ++i) {
			if (player.expertise[i] < project.cost[i]) {
				return false;
			}
		}
		return true;
	}

	@Override
	protected void populateMessages(Properties p) {
		p.put("notEnoughMolecules", "Invalid CONNECT: there are no %s type molecules left");
		p.put("trayIsFull", "Invalid CONNECT: your robot may not carry data for more than " + MAX_TRAY + " samples");
		p.put("storageIsFull", "Invalid CONNECT: your robot may not carry more than " + MAX_STORAGE + " molecules");
		p.put("nullIsInvalidSample", "Invalid CONNECT: you must specify a Sample ID to connect to this module");
		p.put("sampleNotFound", "Invalid CONNECT: the sample %d is not available");
		p.put("badSampleRank", "Invalid CONNECT: there is no sample with rank %s");
		p.put("sampleNotInTray", "Invalid CONNECT: you are not carrying sample %d");
		p.put("unknownMoleculeType", "Invalid CONNECT: invalid molecule %s");
		p.put("cannotAffordSample", "Invalid CONNECT: you do not have enough molecules/expertise to launch research on sample %d");
		p.put("connectToNothing", "Invalid CONNECT: you must go to a module before using the connect command");
		p.put("InvalidConnect", "Invalid CONNECT");
		p.put("ProjectTooltip", "$0 completes a science project!");
		p.put("production", "$%d researched medecine for sample %d, scored %d health points and gained expertise in molecule %s");
		p.put("productionNoGain", "$%d researched medecine for sample %d, scored %d health points");
		p.put("upload", "$%d stores sample %d on the cloud.");
		p.put("newSample", "$%d receives sample %d.");
		p.put("download", "$%d downloads sample %d from the cloud.");
		p.put("takeMolecule", "$%d receives a %s molecule.");
		p.put("etaSAMPLES", "$%d will arrive at the samples module in %d turns");
		p.put("etaDIAGNOSIS", "$%d will arrive at the diagnosis module in %d turns");
		p.put("etaMOLECULES", "$%d will arrive at the molecules module in %d turns");
		p.put("etaLABORATORY", "$%d will arrive at the laboratory module in %d turns");
		p.put("etaSAMPLESsingular", "$%d will arrive at the samples module in %d turn");
		p.put("etaDIAGNOSISsingular", "$%d will arrive at the diagnosis module in %d turn");
		p.put("etaMOLECULESsingular", "$%d will arrive at the molecules module in %d turn");
		p.put("etaLABORATORYsingular", "$%d will arrive at the laboratory module in %d turn");
		p.put("diagnosis", "$%d has diagnosed sample %d");
		p.put("projectCompletion", "$%d has completed the science project %d and scores " + SCIENCE_PROJECT_VALUE + " health points.");

	}

	@Override
	protected String[] getInitDataForView() {
		List<String> lines = new ArrayList<>();
		lines.add(SCIENCE_PROJECT_VALUE + " " + LEAGUE_LEVEL);

		lines.add(String.valueOf(scienceProjects.size()));
		for (ScienceProject project : scienceProjects) {
			lines.add(resourceArrayToString(project.cost));
		}

		lines.add(String.valueOf(storedSamples.size()));
		for (Sample sample : storedSamples) {
			lines.add(join(sample.id, resourceArrayToString(sample.cost), sample.rank, sample.life, sample.expertise));
		}

		lines.add(0, String.valueOf(lines.size() + 1));
		return lines.toArray(new String[lines.size()]);
	}

	private String resourceArrayToString(int[] array) {
		return Arrays.stream(array).mapToObj(Integer::toString).collect(Collectors.joining(" "));
	}

	protected String[] getFrameDataForView(int round, int frame, boolean keyFrame) {
		List<String> lines = new ArrayList<>();

		// Players
		for (PlayerData player : players) {
			Integer total = distances.get(new ModulePair(player.target, player.from));

			StringJoiner joiner = new StringJoiner(" ");
			joiner.add(player.target.name());
			joiner.add(player.from.name());
			joiner.add(String.valueOf(player.eta));
			joiner.add(player.moved ? "1" : "0");
			joiner.add((total == null) ? "0" : String.valueOf(total));
			joiner.add(String.valueOf(resourceArrayToString(player.storage)));
			joiner.add(String.valueOf(resourceArrayToString(player.expertise)));
			joiner.add(String.valueOf(player.score));
			joiner.add(player.dead ? "1" : "0");
			joiner.add(";" + (player.message == null ? "" : player.message));
			lines.add(joiner.toString());
		}

		// Resources
		lines.add(Arrays.stream(MoleculeType.values()).map(type -> String.valueOf(molecules.get(type))).collect(Collectors.joining(" ")));

		// Events
		List<String> eventLines = new LinkedList<>();
		for (Transfer transfer : transfers) {
			if (transfer instanceof SampleTransfer) {
				SampleTransfer st = (SampleTransfer) transfer;
				if (st.bound.equals(Bound.FROM_SAMPLES)) {
					eventLines.add(join(EV_NEW_SAMPLE, st.sample.id, resourceArrayToString(st.sample.cost), st.sample.rank, st.sample.life, st.sample.expertise, st.player.index));
				} else if (st.bound.equals(Bound.FROM_DIAGNOSIS)) {
					if (st.clone != null) {
						eventLines.add(join(EV_CLONE_INITAL_SAMPLE, st.clone.id, resourceArrayToString(st.clone.cost), st.clone.rank, st.clone.life, st.clone.expertise));
						eventLines.add(join(EV_TAKE_SAMPLE, st.clone.id, st.player.index));
					} else {
						eventLines.add(join(EV_TAKE_SAMPLE, st.sample.id, st.player.index));
					}
				} else {
					eventLines.add(join(EV_STORE_SAMPLE, st.sample.id, st.player.index));
				}
			} else if (transfer instanceof ResourceTransfer) {
				ResourceTransfer rt = (ResourceTransfer) transfer;
				eventLines.add(join(EV_TAKE_RESOURCE, rt.resourceType, rt.player.index));
			} else {
				ProductionTransfer pt = (ProductionTransfer) transfer;
				eventLines.add(join(EV_PRODUCE, pt.sample.id));
			}
		}
		for (Diagnosis diagnosis : diagnoses) {
			eventLines.add(join(EV_DIAGNOSE, diagnosis.sample.id));
		}
		lines.add(String.valueOf(eventLines.size()));
		lines.addAll(eventLines);

		return lines.toArray(new String[lines.size()]);
	}

	@SafeVarargs
	static final <T> String join(T... v) {
		return Stream.of(v).map(String::valueOf).collect(Collectors.joining(" "));
	}

	@Override
	protected String getGameName() {
		return "Roche";
	}

	@Override
	protected String getHeadlineAtGameStartForConsole() {
		return null;
	}

	@Override
	protected int getMinimumPlayerCount() {
		return 2;
	}

	@Override
	protected boolean showTooltips() {
		return true;
	}

	@Override
	protected String[] getPlayerActions(int playerIdx, int round) {
		return new String[0];
	}

	@Override
	protected boolean isPlayerDead(int playerIdx) {
		return players.get(playerIdx).dead;
	}

	@Override
	protected String getDeathReason(int playerIdx) {
		return "$" + playerIdx + ": Eliminated!";
	}

	@Override
	protected int getMillisTimeForRound() {
		return 50;
	}

	@Override
	protected int getScore(int playerIdx) {
		PlayerData player = players.get(playerIdx);
		return player.score;
	}

	@Override
	protected String[] getGameSummary(int round) {
		List<String> lines = new ArrayList<>();
		for (int i = 0; i < players.size(); ++i) {
			lines.addAll(getPlayerSummary(i, round));
		}
		return lines.toArray(new String[lines.size()]);
	}

	protected List<String> getPlayerSummary(int playerIdx, int round) {
		List<String> lines = new ArrayList<>();
		PlayerData player = players.get(playerIdx);

		for (Transfer t : transfers) {
			if (t.player == player) {
				Translatable summary = t.getSummary();
				lines.add(translate(summary.code, summary.values));
			}
		}

		for (Diagnosis d : diagnoses) {
			if (d.player == player) {
				lines.add(translate("diagnosis", playerIdx, d.sample.id));
			}
		}

		if (player.isMoving()) {
			lines.add(translate("eta" + player.target + (player.eta == 1 ? "singular" : ""), playerIdx, player.eta));
		}

		for (ProjectCompletion projectCompletion : projectCompletions) {
			if (projectCompletion.player == player) {
				lines.add(translate("projectCompletion", playerIdx, projectCompletion.project.index));
			}
		}

		if (player.dead) {
			if (player.deadAt == round) {
				lines.add(getDeathReason(playerIdx));
			}
		}
		return lines;
	}

	@Override
	protected void setPlayerTimeout(int frame, int round, int playerIdx) {
		PlayerData player = players.get(playerIdx);
		player.die(round);
	}

	@Override
	protected int getMaxRoundCount(int playerCount) {
		return 200;
	}

	@Override
	protected boolean gameOver() {
		return super.gameOver() || players.stream().anyMatch(p -> p.score >= MAX_SCORE);
	}

	public static void main(String... args) throws IOException {
		new Referee(System.in, System.out, System.err).start();
	}
}
