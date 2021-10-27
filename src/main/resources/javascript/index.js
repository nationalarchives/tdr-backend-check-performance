window.addEventListener('load', function () {
  const data = {
    labels: checkNameLabels,
    datasets: [{
      label: 'Average time taken per file check',
      data: timeByCheckData,
      backgroundColor: [
        'rgba(255, 99, 132, 0.2)',
        'rgba(255, 159, 64, 0.2)',
        'rgba(255, 205, 86, 0.2)',
        'rgba(75, 192, 192, 0.2)',
        'rgba(54, 162, 235, 0.2)'
      ],
    }]
  };
  const barConfig = {
    type: 'bar',
    data: data,
  };
  new Chart(
    document.getElementById('timeTaken'),
    barConfig
  );
  const lineFileTypeData = {
    labels: FileTypeLabels,
    datasets: [
      {
        label: 'Download Files',
        data: DownloadFilesFileTypeData,
        borderColor: 'rgba(255, 99, 132, 0.2)',
      },
      {
        label: 'Antivirus',
        data: YaraAvFileTypeData,
        borderColor: 'rgba(255, 159, 64, 0.2)',
      },
      {
        label: 'Checksum',
        data: ChecksumFileTypeData,
        borderColor: 'rgba(255, 205, 86, 0.2)',
      },
      {
        label: 'File Format',
        data: FileFormatFileTypeData,
        borderColor: 'rgba(75, 192, 192, 0.2)',
      },
      {
        label: 'Api Update',
        data: ApiUpdateFileTypeData,
        borderColor: 'rgba(54, 162, 235, 0.2)'
      }
    ]
  };
  new Chart(
    document.getElementById('timeByFileType'),
    {
      type: 'line',
      data: lineFileTypeData,
    }
  );
  const lineFileSizeData = {
    labels: FileSizeLabels,
    datasets: [
      {
        label: 'Download Files',
        data: DownloadFilesFileSizeData,
        borderColor: 'rgba(255, 99, 132, 0.2)',
      },
      {
        label: 'Antivirus',
        data: YaraAvFileSizeData,
        borderColor: 'rgba(255, 159, 64, 0.2)',
      },
      {
        label: 'Checksum',
        data: ChecksumFileSizeData,
        borderColor: 'rgba(255, 205, 86, 0.2)',
      },
      {
        label: 'File Format',
        data: FileFormatFileSizeData,
        borderColor: 'rgba(75, 192, 192, 0.2)',
      },
      {
        label: 'Api Update',
        data: ApiUpdateFileSizeData,
        borderColor: 'rgba(54, 162, 235, 0.2)'
      }
    ]
  };
  new Chart(
    document.getElementById('timeByFileSize'),
    {
      type: 'line',
      data: lineFileSizeData,
    }
  );
}, false);
