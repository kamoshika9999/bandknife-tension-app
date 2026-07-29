namespace BandKnifeViewer;

/// <summary>工場 LAN 上の共有 DATA フォルダ。GAS がドライブへ書き込む内容と同一のコピーを置く。</summary>
public static class SharedFolderPaths
{
  public const string DefaultDataFolder =
    @"\\192.168.0.101\共有フォルダ\湖南工場\湖南共有\002  加工G\●バンドナイフ張力測定\DATA";

  public const string RecordsFileName = "records.csv";
  public const string EquipmentFileName = "equipment.json";
  public const string DriveFolderName = "バンドナイフ張力測定";
}
