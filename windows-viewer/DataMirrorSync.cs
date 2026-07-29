using System.Globalization;
using System.IO;
using System.Text;
using System.Text.Json;

namespace BandKnifeViewer;

/// <summary>
/// GAS が Google ドライブへ書き込んだ records.csv / equipment.json を
/// 工場共有フォルダへミラーする。起動時の過不足修正と、実行中の追記に使う。
/// </summary>
public static class DataMirrorSync
{
  private static readonly JsonSerializerOptions JsonOptions = new()
  {
    PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
    WriteIndented = true
  };

  private static readonly object SyncLock = new();

  public sealed class SyncReport
  {
    public bool ShareAccessible { get; init; }
    public bool DriveSourceFound { get; init; }
    public int RecordsAdded { get; init; }
    public bool EquipmentUpdated { get; init; }
    public string? Error { get; init; }

    public bool AnyChange => RecordsAdded > 0 || EquipmentUpdated;
  }

  /// <summary>ドライブ同期フォルダ → 共有 DATA フォルダへ反映する。</summary>
  public static SyncReport SyncFromDrive(string? driveFolder, string shareFolder)
  {
    lock (SyncLock)
    {
      try
      {
        if (!Directory.Exists(shareFolder))
        {
          return new SyncReport
          {
            ShareAccessible = false,
            DriveSourceFound = !string.IsNullOrEmpty(driveFolder) && Directory.Exists(driveFolder)
          };
        }

        if (string.IsNullOrEmpty(driveFolder) || !Directory.Exists(driveFolder))
        {
          return new SyncReport { ShareAccessible = true, DriveSourceFound = false };
        }

        Directory.CreateDirectory(shareFolder);
        var recordsAdded = MergeRecords(driveFolder, shareFolder);
        var equipmentUpdated = MergeEquipment(driveFolder, shareFolder);
        return new SyncReport
        {
          ShareAccessible = true,
          DriveSourceFound = true,
          RecordsAdded = recordsAdded,
          EquipmentUpdated = equipmentUpdated
        };
      }
      catch (Exception ex)
      {
        return new SyncReport
        {
          ShareAccessible = Directory.Exists(shareFolder),
          DriveSourceFound = !string.IsNullOrEmpty(driveFolder) && Directory.Exists(driveFolder),
          Error = ex.Message
        };
      }
    }
  }

  /// <summary>共有フォルダ上の 1 件の測定記録を GAS と同じ形式で追記する（重複は無視）。</summary>
  public static bool AppendRecordIfMissing(string shareFolder, GasRecord record)
  {
    lock (SyncLock)
    {
      if (!Directory.Exists(shareFolder)) return false;
      var path = Path.Combine(shareFolder, SharedFolderPaths.RecordsFileName);
      var existing = File.Exists(path) ? File.ReadAllText(path, Encoding.UTF8) : "";
      if (existing.Contains($"{record.Timestamp},")) return false;

      var header = "timestamp,equipmentName,frequencyHz,tensionN,passed,sampleCount,stdDev,ci95Lower,ci95Upper,comment\n";
      if (string.IsNullOrEmpty(existing))
        File.WriteAllText(path, header + record.ToCsvRow(), Encoding.UTF8);
      else
        File.AppendAllText(path, record.ToCsvRow(), Encoding.UTF8);
      return true;
    }
  }

  /// <summary>設備マスターを共有フォルダへ書き込む（リビジョンが新しいときだけ）。</summary>
  public static bool WriteEquipmentIfNewer(string shareFolder, EquipmentMaster master)
  {
    lock (SyncLock)
    {
      if (!Directory.Exists(shareFolder)) return false;
      var path = Path.Combine(shareFolder, SharedFolderPaths.EquipmentFileName);
      if (File.Exists(path))
      {
        var current = JsonSerializer.Deserialize<EquipmentMaster>(
          File.ReadAllText(path, Encoding.UTF8), JsonOptions);
        if (current != null && current.Revision >= master.Revision) return false;
      }
      Directory.CreateDirectory(shareFolder);
      EquipmentStore.Save(shareFolder, master);
      return true;
    }
  }

  private static int MergeRecords(string driveFolder, string shareFolder)
  {
    var drivePath = Path.Combine(driveFolder, SharedFolderPaths.RecordsFileName);
    var sharePath = Path.Combine(shareFolder, SharedFolderPaths.RecordsFileName);

    var merged = new Dictionary<long, string>();
    if (File.Exists(sharePath)) LoadRecords(sharePath, merged);
    var before = merged.Count;
    if (File.Exists(drivePath)) LoadRecords(drivePath, merged);

    if (merged.Count == before && !File.Exists(sharePath) && merged.Count == 0)
      return 0;

    WriteRecords(sharePath, merged);
    return merged.Count - before;
  }

  private static bool MergeEquipment(string driveFolder, string shareFolder)
  {
    var drivePath = Path.Combine(driveFolder, SharedFolderPaths.EquipmentFileName);
    if (!File.Exists(drivePath)) return false;

    var driveMaster = EquipmentStore.Load(driveFolder);
    if (driveMaster.Revision <= 0 && driveMaster.Equipment.Count == 0) return false;

    var sharePath = Path.Combine(shareFolder, SharedFolderPaths.EquipmentFileName);
    if (File.Exists(sharePath))
    {
      var shareMaster = EquipmentStore.Load(shareFolder);
      if (shareMaster.Revision >= driveMaster.Revision) return false;
    }

    EquipmentStore.Save(shareFolder, driveMaster);
    return true;
  }

  private static void LoadRecords(string path, Dictionary<long, string> into)
  {
    var lines = File.ReadAllLines(path, Encoding.UTF8);
    for (var i = 1; i < lines.Length; i++)
    {
      var line = lines[i];
      if (string.IsNullOrWhiteSpace(line)) continue;
      var ts = ParseTimestamp(line);
      if (ts > 0) into[ts] = line;
    }
  }

  private static void WriteRecords(string path, Dictionary<long, string> records)
  {
    var header = "timestamp,equipmentName,frequencyHz,tensionN,passed,sampleCount,stdDev,ci95Lower,ci95Upper,comment";
    var sb = new StringBuilder(header).Append('\n');
    foreach (var line in records.OrderBy(kv => kv.Key).Select(kv => kv.Value))
      sb.AppendLine(line);
    File.WriteAllText(path, sb.ToString(), Encoding.UTF8);
  }

  private static long ParseTimestamp(string line)
  {
    var comma = line.IndexOf(',');
    if (comma <= 0) return 0;
    return long.TryParse(line[..comma], NumberStyles.Integer, CultureInfo.InvariantCulture, out var ts) ? ts : 0;
  }
}

/// <summary>drive-upload.gs の appendToCsv と同じ 1 行。</summary>
public sealed class GasRecord
{
  public long Timestamp { get; init; }
  public string EquipmentName { get; init; } = "";
  public double FrequencyHz { get; init; }
  public double TensionN { get; init; }
  public bool Passed { get; init; }
  public int SampleCount { get; init; } = 1;
  public double StdDev { get; init; }
  public double Ci95Lower { get; init; }
  public double Ci95Upper { get; init; }
  public string Comment { get; init; } = "";

  public string ToCsvRow()
  {
    var escaped = Comment.Replace("\"", "\"\"");
    return string.Join(",",
      Timestamp,
      EquipmentName,
      FrequencyHz.ToString(CultureInfo.InvariantCulture),
      TensionN.ToString(CultureInfo.InvariantCulture),
      Passed.ToString().ToLowerInvariant(),
      SampleCount,
      StdDev.ToString(CultureInfo.InvariantCulture),
      Ci95Lower.ToString(CultureInfo.InvariantCulture),
      Ci95Upper.ToString(CultureInfo.InvariantCulture),
      $"\"{escaped}\""
    ) + "\n";
  }
}
