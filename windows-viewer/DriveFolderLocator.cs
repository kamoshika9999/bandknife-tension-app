using System.IO;

namespace BandKnifeViewer;

/// <summary>Google Drive for Desktop が同期する「バンドナイフ張力測定」フォルダを探す。</summary>
public static class DriveFolderLocator
{
  public static string? FindDriveSourceFolder()
  {
    var envFolder = Environment.GetEnvironmentVariable("BANDKNIFE_DRIVE_FOLDER");
    if (!string.IsNullOrWhiteSpace(envFolder) && Directory.Exists(envFolder))
      return envFolder;

    var profile = Environment.GetFolderPath(Environment.SpecialFolder.UserProfile);
    var candidates = new[]
    {
      Path.Combine(profile, "マイドライブ", SharedFolderPaths.DriveFolderName),
      Path.Combine("G:", "マイドライブ", SharedFolderPaths.DriveFolderName),
      Path.Combine("D:", "マイドライブ", SharedFolderPaths.DriveFolderName),
      Path.Combine(profile, "Google Drive", SharedFolderPaths.DriveFolderName),
      Path.Combine("G:", "Google Drive", SharedFolderPaths.DriveFolderName),
      Path.Combine("D:", "Google Drive", SharedFolderPaths.DriveFolderName),
    };
    return candidates.FirstOrDefault(Directory.Exists);
  }
}
