package madoku.craft.hud;

/** Client payload subsystem for synchronized HUD state. */
public final class HudPayloadManager {
	private static final int DEFAULT_MAX_HUNGER = 20;
	private static volatile long serverDay;
	private static volatile int serverHour = 6;
	private static volatile int serverMinute;
	private static volatile boolean hasServerTime;
	private static volatile int serverDifficulty = 1;
	private static volatile boolean hasServerDifficulty;
	private static volatile String serverSeason = "spring";
	private static volatile int serverSeasonDay;
	private static volatile int serverSeasonLengthDays = 28;
	private static volatile boolean hasServerSeason;
	private static volatile double serverTemperature = 50.0D;
	private static volatile double serverHumidity = 50.0D;
	private static volatile boolean hasServerClimate;
	private static volatile int serverHungerCurrent;
	private static volatile int serverHungerMax = DEFAULT_MAX_HUNGER;
	private static volatile boolean hasServerHunger;

	private HudPayloadManager() {
	}

	public static void initialize() {
		reset();
	}

	public static void reset() {
		clearServerTime();
		clearServerDifficulty();
		clearServerSeason();
		clearServerClimate();
		clearServerHunger();
	}

	public static void setServerTime(long day, int hour, int minute) {
		serverDay = Math.max(0L, day);
		serverHour = Math.floorMod(hour, 24);
		serverMinute = Math.floorMod(minute, 60);
		hasServerTime = true;
	}

	public static void clearServerTime() {
		serverDay = 0L;
		serverHour = 6;
		serverMinute = 0;
		hasServerTime = false;
	}

	public static boolean hasServerTime() { return hasServerTime; }
	public static long getServerDay() { return serverDay; }
	public static int getServerHour() { return serverHour; }
	public static int getServerMinute() { return serverMinute; }

	public static void setServerDifficulty(int level) {
		serverDifficulty = Math.max(1, level);
		hasServerDifficulty = true;
	}

	public static void clearServerDifficulty() {
		serverDifficulty = 1;
		hasServerDifficulty = false;
	}

	public static boolean hasServerDifficulty() { return hasServerDifficulty; }
	public static int getServerDifficulty() { return serverDifficulty; }

	public static void setServerSeason(String season) {
		if (season == null || season.isBlank()) {
			clearServerSeason();
			return;
		}
		serverSeason = season;
		serverSeasonDay = 0;
		serverSeasonLengthDays = 28;
		hasServerSeason = true;
	}

	public static void clearServerSeason() {
		serverSeason = "spring";
		serverSeasonDay = 0;
		serverSeasonLengthDays = 28;
		hasServerSeason = false;
	}

	public static boolean hasServerSeason() { return hasServerSeason; }
	public static String getServerSeason() { return serverSeason; }
	public static int getServerSeasonDay() { return serverSeasonDay; }
	public static int getServerSeasonLengthDays() { return serverSeasonLengthDays; }

	public static void setServerSeasonProgress(int seasonDay, int seasonLengthDays) {
		serverSeasonDay = Math.max(0, seasonDay);
		serverSeasonLengthDays = Math.max(1, seasonLengthDays);
	}

	public static void setServerClimate(double temperature, double humidity) {
		serverTemperature = Double.isFinite(temperature) ? temperature : 50.0D;
		serverHumidity = Double.isFinite(humidity) ? humidity : 50.0D;
		hasServerClimate = true;
	}

	public static void clearServerClimate() {
		serverTemperature = 50.0D;
		serverHumidity = 50.0D;
		hasServerClimate = false;
	}

	public static boolean hasServerClimate() { return hasServerClimate; }
	public static double getServerTemperature() { return serverTemperature; }
	public static double getServerHumidity() { return serverHumidity; }

	public static void setServerHunger(int current, int max) {
		serverHungerCurrent = Math.max(0, current);
		serverHungerMax = Math.max(1, max);
		hasServerHunger = true;
	}

	public static void clearServerHunger() {
		serverHungerCurrent = 0;
		serverHungerMax = DEFAULT_MAX_HUNGER;
		hasServerHunger = false;
	}

	public static boolean hasServerHunger() { return hasServerHunger; }
	public static int getServerHungerCurrent() { return serverHungerCurrent; }
	public static int getServerHungerMax() { return serverHungerMax; }

	public static boolean canConsumeFoodClient(boolean ignoreHunger) {
		if (ignoreHunger || !hasServerHunger) return true;
		return Math.max(0, serverHungerCurrent) < Math.max(1, serverHungerMax);
	}
}
