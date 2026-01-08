function initStockChart(labels, prices, powers, volumes, sellRemains, buyRemains) {
    var ctx = document.getElementById('stockChart').getContext('2d');
    new Chart(ctx, {
        type: 'line',
        data: {
            labels: labels,
            datasets: [
                {
                    label: '현재가',
                    data: prices,
                    borderColor: '#e74c3c', // Red
                    backgroundColor: '#e74c3c',
                    yAxisID: 'y-price',
                    fill: false,
                    tension: 0.1
                },
                {
                    label: '체결강도',
                    data: powers,
                    borderColor: '#3498db', // Blue
                    backgroundColor: '#3498db',
                    yAxisID: 'y-power',
                    fill: false,
                    tension: 0.1
                },
                {
                    label: '매도잔량',
                    data: sellRemains,
                    borderColor: '#2ecc71', // Green
                    backgroundColor: '#2ecc71',
                    yAxisID: 'y-volume',
                    fill: false,
                    borderDash: [5, 5],
                    pointRadius: 0
                },
                {
                    label: '매수잔량',
                    data: buyRemains,
                    borderColor: '#f39c12', // Orange
                    backgroundColor: '#f39c12',
                    yAxisID: 'y-volume',
                    fill: false,
                    borderDash: [5, 5],
                    pointRadius: 0
                },
                {
                    label: '거래량',
                    data: volumes,
                    type: 'bar',
                    backgroundColor: 'rgba(149, 165, 166, 0.5)', // Grey
                    yAxisID: 'y-volume'
                }
            ]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            interaction: {
                mode: 'index',
                intersect: false,
            },
            plugins: {
                legend: {
                    position: 'top',
                },
                tooltip: {
                    enabled: true
                }
            },
            scales: {
                x: {
                    grid: {
                        display: false
                    }
                },
                'y-price': {
                    type: 'linear',
                    display: true,
                    position: 'left',
                    title: { display: true, text: '가격 (원)' },
                    grid: {
                        color: 'rgba(0, 0, 0, 0.05)'
                    }
                },
                'y-power': {
                    type: 'linear',
                    display: true,
                    position: 'right',
                    title: { display: true, text: '체결강도 (%)' },
                    grid: { drawOnChartArea: false }
                },
                'y-volume': {
                    type: 'linear',
                    display: true,
                    position: 'right',
                    title: { display: true, text: '수량 (주)' },
                    grid: { drawOnChartArea: false }
                }
            }
        }
    });
}
