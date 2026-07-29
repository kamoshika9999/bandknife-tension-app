using System.IO;
using System.Text;
using System.Text.Json;

namespace BandKnifeViewer;

public static class EquipmentStore
{
  private static readonly JsonSerializerOptions JsonOptions = new()
  {
    PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
    WriteIndented = true
  };

  public const string FileName = "equipment.json";

  public static string PathFor(string dataFolder) => Path.Combine(dataFolder, FileName);

  public static EquipmentMaster Load(string dataFolder)
  {
    var path = PathFor(dataFolder);
    if (!File.Exists(path)) return new EquipmentMaster();
    var json = File.ReadAllText(path, Encoding.UTF8);
    return JsonSerializer.Deserialize<EquipmentMaster>(json, JsonOptions) ?? new EquipmentMaster();
  }

  public static void Save(string dataFolder, EquipmentMaster master)
  {
    var path = PathFor(dataFolder);
    var json = JsonSerializer.Serialize(master, JsonOptions);
    File.WriteAllText(path, json, Encoding.UTF8);
  }
}
