/**
 * GLM-5.3-Flash 谐音标注（智谱 API，客户端直连）。
 *
 * ⚠️ key 暂时内置在客户端（用户明确要求）；后续为安全起见迁移到
 * apiserver 代理调用，届时只需改本文件（endpoint 已收敛到此处）。
 *
 * 定位是纯「标注」任务：输入原词行数组，逐行输出中文谐音，
 * 不生成/不修改原词与粤拼。
 */
const API_URL = 'https://open.bigmodel.cn/api/paas/v4/chat/completions'
const API_KEY = '783275b708024d14bb4be9e4246004e0.UiDz1BIIZEotVX6W'
const MODEL = 'glm-5.3-flash'

const SYSTEM_PROMPT =
  '你是一个粤语谐音标注助手。用户给你若干行粤语歌词（普通话汉字写法），' +
  '你为每一行逐字标注「中文谐音」：\n' +
  '1. 该行每个字对应一个谐音汉字，用普通话读出来尽量接近该字在粤语中的发音；\n' +
  '2. 谐音字与空格分隔，字数与顺序和原行一致；\n' +
  '3. 只输出一个 JSON 对象：{"lines": ["谐 音 1", "谐 音 2", ...]}，' +
  'lines 行数与输入一致、顺序一致；\n' +
  '4. 不要输出任何解释、markdown 代码块或额外文字。'

function buildUserPrompt(lines) {
  const numbered = lines.map((l, i) => (i + 1) + '. ' + l).join('\n')
  return (
    '以下是粤语歌词（共 ' + lines.length + ' 行），请逐行逐字标注中文谐音：\n' +
    numbered +
    '\n\n只输出 JSON：{"lines": [...]}'
  )
}

/** 解析模型返回（容忍代码块包裹、前后多余文字）→ 谐音字符串数组 */
function parseHomophones(content) {
  let text = String(content || '').trim()
  if (text.indexOf('```') === 0) {
    text = text.substring(3).trim()
    if (text.indexOf('json') === 0) text = text.substring(4).trim()
    if (text.slice(-3) === '```') text = text.slice(0, -3).trim()
  }
  const start = text.indexOf('{')
  const end = text.lastIndexOf('}')
  if (start < 0 || end <= start) {
    throw new Error('模型返回中未找到 JSON，请重试')
  }
  let root
  try {
    root = JSON.parse(text.substring(start, end + 1))
  } catch (e) {
    throw new Error('模型返回的 JSON 解析失败，请重试')
  }
  const arr = Array.isArray(root.lines) ? root.lines : []
  const out = arr.map((x) => String(x == null ? '' : x).trim())
  if (!out.length) throw new Error('模型返回的谐音为空，请重试')
  return out
}

/** 为原词行数组逐行标注谐音 → Promise<string[]> */
function annotate(lines) {
  return new Promise((resolve, reject) => {
    wx.request({
      url: API_URL,
      method: 'POST',
      header: {
        'Content-Type': 'application/json',
        Authorization: 'Bearer ' + API_KEY
      },
      timeout: 120000,
      data: {
        model: MODEL,
        temperature: 0.3,
        stream: false,
        messages: [
          { role: 'system', content: SYSTEM_PROMPT },
          { role: 'user', content: buildUserPrompt(lines) }
        ]
      },
      success(res) {
        if (res.statusCode >= 200 && res.statusCode < 300) {
          try {
            const content = res.data.choices[0].message.content
            resolve(parseHomophones(content))
          } catch (e) {
            reject(new Error('无法解析模型响应'))
          }
        } else {
          let body = ''
          try {
            body = JSON.stringify(res.data).slice(0, 200)
          } catch (e) {
            body = ''
          }
          reject(new Error('GLM HTTP ' + res.statusCode + '：' + body))
        }
      },
      fail(err) {
        reject(
          new Error('GLM 请求失败：' + (err && err.errMsg ? err.errMsg : '网络错误'))
        )
      }
    })
  })
}

module.exports = { annotate, MODEL }
