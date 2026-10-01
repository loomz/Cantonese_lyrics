/**
 * 歌词展示模板：背景色 + 三行文字颜色（粤拼/普通话/中文谐音）
 * 与安卓、iOS 端保持一致
 */
const list = [
  { name: '经典黑', background: '#000000', jyutping: '#8A8A8A', mandarin: '#FFFFFF', homophone: '#B0B0B0' },
  { name: '深夜蓝', background: '#0A1929', jyutping: '#5B7A99', mandarin: '#A8D8FF', homophone: '#7FA8CC' },
  { name: '日落橘', background: '#1A0E05', jyutping: '#9C6B3F', mandarin: '#FFB25E', homophone: '#C98A4B' },
  { name: '森林绿', background: '#07130B', jyutping: '#4F7A5A', mandarin: '#8FE3A1', homophone: '#6FA97C' },
  { name: '樱花粉', background: '#FFF0F3', jyutping: '#B98A94', mandarin: '#C2185B', homophone: '#A05A6E' },
  { name: '复古米', background: '#F5EFE0', jyutping: '#9C8F76', mandarin: '#5D4037', homophone: '#8D6E63' }
]

module.exports = { list }
