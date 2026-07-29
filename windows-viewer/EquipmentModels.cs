using System.Text.Json.Serialization;

namespace BandKnifeViewer;

public class EquipmentMaster
{
  [JsonPropertyName("revision")]
  public int Revision { get; set; }

  [JsonPropertyName("updatedAt")]
  public long UpdatedAt { get; set; }

  [JsonPropertyName("updatedBy")]
  public string UpdatedBy { get; set; } = "";

  [JsonPropertyName("equipment")]
  public List<EquipmentItem> Equipment { get; set; } = new();
}

public class EquipmentItem
{
  [JsonPropertyName("uuid")]
  public string Uuid { get; set; } = "";

  [JsonPropertyName("name")]
  public string Name { get; set; } = "";

  [JsonPropertyName("massPerMeter")]
  public double MassPerMeter { get; set; }

  [JsonPropertyName("spanMeters")]
  public double SpanMeters { get; set; }

  [JsonPropertyName("standardTension")]
  public double StandardTension { get; set; }

  [JsonPropertyName("specLower")]
  public double SpecLower { get; set; }

  [JsonPropertyName("specUpper")]
  public double SpecUpper { get; set; }

  [JsonPropertyName("useHzMode")]
  public bool UseHzMode { get; set; }

  [JsonPropertyName("standardHz")]
  public double StandardHz { get; set; }

  [JsonPropertyName("specHzLower")]
  public double SpecHzLower { get; set; }

  [JsonPropertyName("specHzUpper")]
  public double SpecHzUpper { get; set; }

  [JsonPropertyName("widthMm")]
  public double WidthMm { get; set; }

  [JsonPropertyName("thicknessMm")]
  public double ThicknessMm { get; set; }

  [JsonPropertyName("density")]
  public double Density { get; set; } = 7850.0;

  public string ModeText => UseHzMode ? "Hz" : "N";
  public string StandardText => UseHzMode ? $"{StandardHz:F1} Hz" : $"{StandardTension:N0} N";
  public string SpecText => UseHzMode
    ? $"{SpecHzLower:F1}–{SpecHzUpper:F1} Hz"
    : $"{SpecLower:N0}–{SpecUpper:N0} N";
  public string MassText => $"{MassPerMeter:F3} kg/m";
  public string SpanText => $"{SpanMeters:F2} m";

  public EquipmentItem CloneAsNew()
  {
    return new EquipmentItem
    {
      Uuid = Guid.NewGuid().ToString(),
      Name = $"{Name}（コピー）",
      MassPerMeter = MassPerMeter,
      SpanMeters = SpanMeters,
      StandardTension = StandardTension,
      SpecLower = SpecLower,
      SpecUpper = SpecUpper,
      UseHzMode = UseHzMode,
      StandardHz = StandardHz,
      SpecHzLower = SpecHzLower,
      SpecHzUpper = SpecHzUpper,
      WidthMm = WidthMm,
      ThicknessMm = ThicknessMm,
      Density = Density
    };
  }

  public static double MassFromDimensions(double widthMm, double thicknessMm, double densityKgM3)
    => (widthMm / 1000.0) * (thicknessMm / 1000.0) * densityKgM3;
}

public static class EquipmentValidator
{
  public static string? Validate(EquipmentItem item, int index)
  {
    var where = $"{index + 1} 件目の設備";
    if (string.IsNullOrWhiteSpace(item.Uuid)) return $"{where}に識別子がありません";
    if (string.IsNullOrWhiteSpace(item.Name)) return $"{where}に名前がありません";
    if (item.MassPerMeter <= 0) return $"「{item.Name}」の単位質量が 0 以下です";
    if (item.SpanMeters <= 0) return $"「{item.Name}」のスパン長が 0 以下です";
    if (item.UseHzMode)
    {
      if (item.SpecHzLower <= 0 || item.SpecHzUpper <= item.SpecHzLower)
        return $"「{item.Name}」の規格（Hz）が正しくありません";
    }
    else if (item.SpecLower <= 0 || item.SpecUpper <= item.SpecLower)
      return $"「{item.Name}」の規格（張力）が正しくありません";
    return null;
  }

  public static string? ValidateAll(List<EquipmentItem> items)
  {
    if (items.Count == 0) return "設備が 1 件もありません。空のマスターには保存できません";
    for (var i = 0; i < items.Count; i++)
    {
      var err = Validate(items[i], i);
      if (err != null) return err;
    }
    var seen = new HashSet<string>();
    foreach (var item in items)
    {
      if (seen.Contains(item.Uuid)) return $"設備の識別子が重複しています（{item.Name}）";
      seen.Add(item.Uuid);
    }
    return null;
  }
}
