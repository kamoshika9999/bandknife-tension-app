using System.Globalization;
using System.Windows;
using System.Windows.Controls;

namespace BandKnifeViewer;

public partial class EquipmentEditDialog : Window
{
  private readonly bool _isNew;

  public EquipmentItem Result { get; private set; }

  public EquipmentEditDialog(EquipmentItem item, bool isNew)
  {
    Result = item;
    _isNew = isNew;
    InitializeComponent();
    TitleText.Text = isNew ? "設備を追加" : "設備を編集";
    Title = TitleText.Text;
    NameBox.Text = item.Name;
    WidthBox.Text = item.WidthMm.ToString(CultureInfo.InvariantCulture);
    ThicknessBox.Text = item.ThicknessMm.ToString(CultureInfo.InvariantCulture);
    MassBox.Text = item.MassPerMeter.ToString(CultureInfo.InvariantCulture);
    SpanBox.Text = item.SpanMeters.ToString(CultureInfo.InvariantCulture);
    StandardBox.Text = item.StandardTension.ToString(CultureInfo.InvariantCulture);
    LowerBox.Text = item.SpecLower.ToString(CultureInfo.InvariantCulture);
    UpperBox.Text = item.SpecUpper.ToString(CultureInfo.InvariantCulture);
    UseHzCheck.IsChecked = item.UseHzMode;
    StandardHzBox.Text = item.StandardHz.ToString(CultureInfo.InvariantCulture);
    HzLowerBox.Text = item.SpecHzLower.ToString(CultureInfo.InvariantCulture);
    HzUpperBox.Text = item.SpecHzUpper.ToString(CultureInfo.InvariantCulture);
    UpdateHzPanel();
  }

  private void DimensionBox_TextChanged(object sender, TextChangedEventArgs e)
  {
    var width = ParseDouble(WidthBox.Text);
    var thickness = ParseDouble(ThicknessBox.Text);
    if (width > 0 && thickness > 0)
      MassBox.Text = EquipmentItem.MassFromDimensions(width, thickness, 7850.0)
        .ToString(CultureInfo.InvariantCulture);
  }

  private void UseHzCheck_Changed(object sender, RoutedEventArgs e) => UpdateHzPanel();

  private void UpdateHzPanel() => HzPanel.Visibility = UseHzCheck.IsChecked == true
    ? Visibility.Visible
    : Visibility.Collapsed;

  private static double ParseDouble(string text)
    => double.TryParse(text, NumberStyles.Float, CultureInfo.InvariantCulture, out var v) ? v : 0;

  private void Cancel_Click(object sender, RoutedEventArgs e)
  {
    DialogResult = false;
    Close();
  }

  private void Save_Click(object sender, RoutedEventArgs e)
  {
    var item = new EquipmentItem
    {
      Uuid = string.IsNullOrWhiteSpace(Result.Uuid) ? Guid.NewGuid().ToString() : Result.Uuid,
      Name = NameBox.Text.Trim(),
      WidthMm = ParseDouble(WidthBox.Text),
      ThicknessMm = ParseDouble(ThicknessBox.Text),
      MassPerMeter = ParseDouble(MassBox.Text),
      SpanMeters = ParseDouble(SpanBox.Text),
      StandardTension = ParseDouble(StandardBox.Text),
      SpecLower = ParseDouble(LowerBox.Text),
      SpecUpper = ParseDouble(UpperBox.Text),
      UseHzMode = UseHzCheck.IsChecked == true,
      StandardHz = ParseDouble(StandardHzBox.Text),
      SpecHzLower = ParseDouble(HzLowerBox.Text),
      SpecHzUpper = ParseDouble(HzUpperBox.Text),
      Density = Result.Density
    };

    var err = EquipmentValidator.Validate(item, 0);
    if (err != null)
    {
      ErrorText.Text = err;
      return;
    }

    if (!item.UseHzMode && item.StandardTension <= 0)
    {
      ErrorText.Text = "標準張力は 0 より大きい値を入力してください";
      return;
    }
    if (item.UseHzMode && item.StandardHz <= 0)
    {
      ErrorText.Text = "標準周波数は 0 より大きい値を入力してください";
      return;
    }

    Result = item;
    DialogResult = true;
    Close();
  }
}
