const XLSX = require('xlsx');
const path = require('path');

const data = [
  ['课程名', '模块', '学分', '是否必修'],
  ['高级语言程序设计', '公共基础', 4.0, '是'],
  ['数据结构', '专业核心', 4.0, '是'],
  ['计算机组成原理', '专业核心', 4.0, '是'],
  ['操作系统', '专业核心', 4.0, '是'],
  ['数据库系统概论', '专业核心', 4.0, '是'],
  ['算法设计与分析', '专业核心', 3.0, '是'],
  ['计算机网络', '专业核心', 3.0, '是'],
  ['编译原理', '专业核心', 3.0, '是'],
  ['人工智能导论', '专业选修', 3.0, '否'],
  ['机器学习', '专业选修', 3.0, '否'],
  ['软件工程', '专业核心', 3.0, '是'],
  ['面向对象程序设计', '专业核心', 3.0, '是']
];

const ws = XLSX.utils.aoa_to_sheet(data);
const wb = XLSX.utils.book_new();
XLSX.utils.book_append_sheet(wb, ws, '课程导入模板');

const outputPath = path.join(__dirname, '..', 'templates', '01-管理员导入模板', 'courses_import_template.xlsx');
XLSX.writeFile(wb, outputPath);
console.log('Successfully generated ' + outputPath);
